package ru.harness.permission;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.harness.TestPermissionConfig;
import ru.harness.agent.AgentProperties;
import ru.harness.tool.FileStateCache;
import ru.harness.tool.Tool;
import ru.harness.tool.ToolContext;
import ru.harness.tool.ToolResult;
import ru.harness.tool.WorkspaceGuard;
import ru.harness.tool.impl.BashTool;
import ru.harness.tool.impl.FileEditTool;
import ru.harness.tool.impl.FileReadTool;
import ru.harness.tool.impl.FileWriteTool;
import ru.harness.tool.impl.GrepTool;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PermissionEvaluatorTest {
    @Test
    void сервисСамПроверяетЗапретАргументовДоОбщегоAsk() {
        var setup = setup(PermissionMode.BYPASS_PERMISSIONS,
                List.of("Bash", "Write"), List.of("Bash(rm *)", "Edit(secret.txt)"), List.of("Bash", "Write"));
        assertThat(bash(setup, "rm secret.txt").behavior()).isEqualTo(PermissionBehavior.DENY);
        var write = new FileWriteTool(guard());
        assertThat(setup.evaluator().evaluate(write, new FileWriteTool.Input("secret.txt", "x"), setup.context()).behavior())
                .isEqualTo(PermissionBehavior.DENY);
    }

    @Test
    void вопросПервойЧастиНеСкрываетЗапретВторой() {
        var s = setup(PermissionMode.DEFAULT, List.of(), List.of("Bash(git push*)"), List.of());
        assertThat(bash(s, "git reset --hard HEAD && git push origin").behavior())
                .isEqualTo(PermissionBehavior.DENY);
    }

    @TempDir Path root;

    private WorkspaceGuard guard() {
        return new WorkspaceGuard(new AgentProperties(20, root.toString(), true));
    }

    private TestPermissionConfig.Setup setup(
            PermissionMode mode, List<String> allow, List<String> deny, List<String> ask) {
        return TestPermissionConfig.setup(
                TestPermissionConfig.properties(mode, allow, deny, ask),
                guard(),
                new FileStateCache());
    }

    private PermissionDecision bash(TestPermissionConfig.Setup s, String command) {
        var tool = new BashTool(guard());
        return s.evaluator().evaluate(tool, new BashTool.Input(command, null, null), s.context());
    }

    private PermissionDecision edit(TestPermissionConfig.Setup s, String path) {
        var tool = new FileEditTool(guard());
        return s.evaluator().evaluate(tool, new FileEditTool.Input(path, "a", "b", null), s.context());
    }

    @Test
    void снаружиСпрашиваемАВBypassПропускаем() {
        // Прежний WorkspaceGuard запрещал выход наружу в любом режиме и не спрашивал никого.
        // Теперь границу держат проверки: по умолчанию внешний путь становится вопросом,
        // а в bypassPermissions проходит молча. Это цена режима, а не упущение.
        assertThat(edit(setup(PermissionMode.DEFAULT, List.of(), List.of(), List.of()), "../outside.txt").behavior())
                .isEqualTo(PermissionBehavior.ASK);
        assertThat(edit(setup(PermissionMode.BYPASS_PERMISSIONS, List.of(), List.of(), List.of()), "../outside.txt").behavior())
                .isEqualTo(PermissionBehavior.ALLOW);
    }

    @Test
    void запретСильнееРежимаИРазрешения() {
        var s =
                setup(
                        PermissionMode.BYPASS_PERMISSIONS,
                        List.of("Bash(git *)"),
                        List.of("Bash(git push*)"),
                        List.of());
        assertThat(bash(s, "git push origin").behavior()).isEqualTo(PermissionBehavior.DENY);
    }

    @Test
    void явныйAskСохраняетсяДажеВBypass() {
        var s =
                setup(
                        PermissionMode.BYPASS_PERMISSIONS,
                        List.of("Bash(*)"),
                        List.of(),
                        List.of("Bash(git push*)"));
        assertThat(bash(s, "git push origin"))
                .satisfies(
                        d -> {
                            assertThat(d.behavior()).isEqualTo(PermissionBehavior.ASK);
                            assertThat(d.askAlways()).isTrue();
                        });
    }

    @Test
    void defaultСпрашиваетПравкуАAcceptEditsРазрешает() {
        assertThat(
                        edit(
                                        setup(
                                                PermissionMode.DEFAULT,
                                                List.of(),
                                                List.of(),
                                                List.of()),
                                        "App.java")
                                .behavior())
                .isEqualTo(PermissionBehavior.ASK);
        assertThat(
                        edit(
                                        setup(
                                                PermissionMode.ACCEPT_EDITS,
                                                List.of(),
                                                List.of(),
                                                List.of()),
                                        "App.java")
                                .behavior())
                .isEqualTo(PermissionBehavior.ALLOW);
    }

    @Test
    void защищённыйПутьСпрашиваетДажеВBypass() {
        assertThat(
                        edit(
                                        setup(
                                                PermissionMode.ACCEPT_EDITS,
                                                List.of("Edit(**)"),
                                                List.of(),
                                                List.of()),
                                        ".git/config")
                                .askAlways())
                .isTrue();
        // Настройки Git, IDE и агента режим не снимает: BYPASS разрешает остальное, но здесь спрашивает.
        assertThat(edit(setup(PermissionMode.BYPASS_PERMISSIONS, List.of(), List.of(), List.of()), ".git/config"))
                .satisfies(d -> {
                    assertThat(d.behavior()).isEqualTo(PermissionBehavior.ASK);
                    assertThat(d.askAlways()).isTrue();
                });
    }

    @Test
    void askПутиСильнееAcceptEditsИAllow() {
        assertThat(
                        edit(
                                        setup(
                                                PermissionMode.ACCEPT_EDITS,
                                                List.of("Edit(src/**)"),
                                                List.of(),
                                                List.of("Edit(src/security/**)")),
                                        "src/security/Auth.java")
                                .behavior())
                .isEqualTo(PermissionBehavior.ASK);
    }

    @Test
    void разрешениеПутиОбрабатываетИнструмент() {
        assertThat(
                        edit(
                                        setup(
                                                PermissionMode.DEFAULT,
                                                List.of("Edit(src/**)"),
                                                List.of(),
                                                List.of()),
                                        "src/App.java")
                                .behavior())
                .isEqualTo(PermissionBehavior.ALLOW);
    }

    @Test
    void EditПравилоРаспространяетсяНаWrite() {
        var s =
                setup(
                        PermissionMode.BYPASS_PERMISSIONS,
                        List.of(),
                        List.of("Edit(secret/**)"),
                        List.of());
        var tool = new FileWriteTool(guard());
        assertThat(
                        s.evaluator()
                                .evaluate(
                                        tool,
                                        new FileWriteTool.Input("secret/key.txt", "x"),
                                        s.context())
                                .behavior())
                .isEqualTo(PermissionBehavior.DENY);
    }

    @Test
    void записьПодчиняетсяПравиламEditАНеRead() {
        // Чтение — правила Read, запись — правила Edit. Чтобы закрыть и правку .env, нужен Edit(.env).
        assertThat(edit(setup(PermissionMode.BYPASS_PERMISSIONS, List.of(), List.of("Read(.env)"), List.of()), ".env").behavior())
                .isEqualTo(PermissionBehavior.ALLOW);
        assertThat(edit(setup(PermissionMode.BYPASS_PERMISSIONS, List.of(), List.of("Edit(.env)"), List.of()), ".env").behavior())
                .isEqualTo(PermissionBehavior.DENY);
    }

    @Test
    void правилоНаВесьReadНеЗакрываетДругиеИнструменты() {
        // Read без скобок — правило на инструмент Read. Grep проверяется по своему имени; правила с путём действуют на всех.
        var whole = setup(PermissionMode.DEFAULT, List.of(), List.of("Read"), List.of());
        var grep = new GrepTool(guard());
        assertThat(whole.evaluator().evaluate(grep, new GrepTool.Input("x", ".", null, null, null), whole.context()).behavior())
                .isEqualTo(PermissionBehavior.ALLOW);
        var byPath = setup(PermissionMode.DEFAULT, List.of(), List.of("Read(src/**)"), List.of());
        assertThat(byPath.evaluator().evaluate(grep, new GrepTool.Input("x", "src/main", null, null, null), byPath.context()).behavior())
                .isEqualTo(PermissionBehavior.DENY);
    }

    @Test
    void частиBashНеИсполняютсяПроверкой() {
        var s = setup(PermissionMode.ACCEPT_EDITS, List.of(), List.of(), List.of());
        assertThat(bash(s, "mkdir build && ./mvnw test").behavior())
                .isEqualTo(PermissionBehavior.ASK);
        assertThat(root.resolve("build")).doesNotExist();
    }

    @Test
    void командаБезГотовогоРазрешенияСпрашивает() {
        assertThat(
                        bash(
                                        setup(
                                                PermissionMode.DEFAULT,
                                                List.of(),
                                                List.of(),
                                                List.of()),
                                        "./mvnw test")
                                .behavior())
                .isEqualTo(PermissionBehavior.ASK);
    }

    @Test
    void запомненнаяКомандаРазрешаетТолькоЕё() {
        var s = setup(PermissionMode.DEFAULT, List.of(), List.of(), List.of());
        var asked = bash(s, "./mvnw test");
        // Вопрос несёт вариант «запомнить»: правило на эту команду в local.
        assertThat(asked.suggestions()).singleElement()
                .isEqualTo(PermissionUpdate.rule(PermissionRule.parse("Bash(./mvnw test)"), PermissionBehavior.ALLOW, PermissionSource.LOCAL));
        asked.suggestions().forEach(s.orders()::apply);
        assertThat(bash(s, "./mvnw test").behavior()).isEqualTo(PermissionBehavior.ALLOW);
        assertThat(bash(s, "./mvnw test -X").behavior()).isEqualTo(PermissionBehavior.ASK);
    }

    @Test
    void командуСоЗвёздочкойНеПредлагаемЗапомнить() {
        // Bash(echo *) в настройках означал бы любой echo: звёздочка в конце — префикс. Запомнить такое нельзя.
        var s = setup(PermissionMode.DEFAULT, List.of(), List.of(), List.of());
        var asked = bash(s, "echo *");
        assertThat(asked.behavior()).isEqualTo(PermissionBehavior.ASK);
        assertThat(asked.suggestions()).isEmpty();
    }

    @Test
    void правилоСессииВидноВТомЖеКонтексте() {
        var s = setup(PermissionMode.DEFAULT, List.of(), List.of(), List.of());
        assertThat(edit(s, "App.java").behavior()).isEqualTo(PermissionBehavior.ASK);
        s.orders().apply(PermissionUpdate.rule(
                new PermissionRule("Edit", "//" + guard().root().toString().replace('\\', '/') + "/**"),
                PermissionBehavior.ALLOW,
                PermissionSource.SESSION));
        assertThat(edit(s, "App.java").behavior()).isEqualTo(PermissionBehavior.ALLOW);
    }

    @Test
    void внешнийКаталогМожноРазрешитьНаСессию() {
        var s = setup(PermissionMode.DEFAULT, List.of(), List.of(), List.of());
        Path outside = root.resolveSibling(root.getFileName() + "-outside");
        assertThat(
                        new FileReadTool(guard())
                                .checkPermissions(
                                        new FileReadTool.Input(
                                                outside.resolve("x.txt").toString(), null, null),
                                        s.context())
                                .behavior())
                .isEqualTo(PermissionBehavior.ASK);
        s.orders().apply(PermissionUpdate.directory(outside.toString()));
        assertThat(
                        new FileReadTool(guard())
                                .checkPermissions(
                                        new FileReadTool.Input(
                                                outside.resolve("x.txt").toString(), null, null),
                                        s.context())
                                .behavior())
                .isEqualTo(PermissionBehavior.ALLOW);
    }

    @Test
    void общийЗапретНеВызываетПроверкуИнструмента() {
        var s = setup(PermissionMode.DEFAULT, List.of(), List.of("Probe"), List.of());
        Tool<String> tool =
                new Tool<>() {
                    public String name() {
                        return "Probe";
                    }

                    public String description() {
                        return "";
                    }

                    public Class<String> inputType() {
                        return String.class;
                    }

                    public PermissionDecision checkPermissions(String i, ToolContext c) {
                        throw new AssertionError("не должны попасть сюда");
                    }

                    public ToolResult call(String i, ToolContext c) {
                        throw new AssertionError();
                    }
                };
        assertThat(s.evaluator().evaluate(tool, "x", s.context()).behavior())
                .isEqualTo(PermissionBehavior.DENY);
    }

    @Test
    void битоеПравилоНеПроглатываем() {
        assertThatThrownBy(() -> PermissionRule.parse("Bash(git *"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Внешний каталог с файлом; путь настоящий, чтобы правило //... совпало и на системах с /tmp-ссылкой. */

    // Матрица Bash с файловыми путями. Внешний путь даёт вопрос; внутренний обычный ASK решают правила Bash и режим.

    // Составные команды, кроме &&, не разбираем: даже с запретом внутри это обязательный вопрос, а не отказ.

    @Test
    void неразобраннаяСоставнаяКомандаВсегдаОбязательныйВопрос() {
        var s = setup(PermissionMode.BYPASS_PERMISSIONS, List.of(), List.of("Bash(rm *)"), List.of());
        // Bypass такой вопрос не снимает. Запрет внутри строки мы не ищем: это делает разбор на подкоманды в оригинале.
        for (String command : List.of("echo farm; ls", "echo \"a;b\"", "echo hi; rm x", "echo hi | rm x", "echo $(rm x)")) {
            assertThat(bash(s, command))
                    .satisfies(d -> {
                        assertThat(d.behavior()).isEqualTo(PermissionBehavior.ASK);
                        assertThat(d.askAlways()).isTrue();
                    });
        }
    }
}
