package ru.harness.tool.impl;

import ru.harness.TestPermissionConfig;
import ru.harness.agent.AgentProperties;
import ru.harness.permission.PermissionBehavior;
import ru.harness.tool.FileStateCache;
import ru.harness.tool.ToolContext;
import ru.harness.tool.ToolResult;
import ru.harness.tool.WorkspaceGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BashToolTest {

    @TempDir Path workspace;

    private BashTool tool;
    private final ToolContext context =
            TestPermissionConfig.context(new FileStateCache());

    @BeforeEach
    void setUp() {
        tool = new BashTool(new WorkspaceGuard(new AgentProperties(20, workspace.toString(), true)));
    }

    private ToolResult run(String command) {
        return tool.call(new BashTool.Input(command, null, null), context);
    }

    private PermissionBehavior behaviorFor(String command) {
        return tool.checkPermissions(new BashTool.Input(command, null, null), context).behavior();
    }

    @Test
    void выполняетКомандуИВозвращаетВывод() {
        ToolResult result = run("echo привет");

        assertThat(result.error()).isFalse();
        assertThat(result.content()).contains("привет").contains("код возврата: 0");
    }

    @Test
    void ненулевойКодЭтоРезультатАНеОшибка() {
        // Упавшие тесты — ровно то, что агент и просил узнать.
        ToolResult result = run("exit 3");

        assertThat(result.error()).isFalse();
        assertThat(result.content()).contains("код возврата: 3");
    }

    @Test
    void несуществующаяКомандаТожеВозвращаетсяТекстом() {
        ToolResult result = run("такой-команды-нет-12345");

        assertThat(result.error()).isFalse();
        assertThat(result.content()).doesNotContain("код возврата: 0");
    }

    @Test
    void командаВыполняетсяВРабочейПапке() {
        String pwd = System.getProperty("os.name").toLowerCase().contains("win") ? "cd" : "pwd";

        assertThat(run(pwd).content()).contains(workspace.getFileName().toString());
    }

    @Test
    void зависшаяКомандаПрерываетсяПоТаймауту() {
        String sleep =
                System.getProperty("os.name").toLowerCase().contains("win")
                        ? "ping -n 20 127.0.0.1 > nul"
                        : "sleep 20";
        // Работаем не в @TempDir: убитый процесс какое-то время держит рабочую папку,
        // и JUnit не может её удалить после теста.
        BashTool inProject = new BashTool(new WorkspaceGuard(new AgentProperties(20, ".", true)));

        ToolResult result = inProject.call(new BashTool.Input(sleep, null, 1), context);

        assertThat(result.error()).isTrue();
        assertThat(result.content()).contains("не уложилась");
    }

    @Test
    void дочернийПроцессПолучаетНастроеннуюJDK() {
        // Агент — программа на Java, и запущенная им сборка обязана найти компилятор.
        // На машине может не быть ни JAVA_HOME, ни java в PATH.
        String echo =
                System.getProperty("os.name").toLowerCase().contains("win")
                        ? "echo %JAVA_HOME%"
                        : "echo $JAVA_HOME";

        ToolResult result = run(echo);

        String configured = System.getenv("JAVA_HOME");
        assertThat(result.content())
                .contains(configured == null ? System.getProperty("java.home") : configured);
        assertThat(run("java -version").content()).contains("код возврата: 0");
    }

    @Test
    void системныйPATHНеЗатирается() {
        // Добавляя JDK в PATH, легко затереть остальное: на Windows переменная
        // называется Path, и обращение к «PATH» вернёт пустоту.
        // Тогда в дочернем процессе перестанут находиться даже системные команды.
        String systemCommand =
                System.getProperty("os.name").toLowerCase().contains("win")
                        ? "where where"
                        : "which ls";

        assertThat(run(systemCommand).content()).contains("код возврата: 0");
    }

    @Test
    void длинныйВыводОбрезаетсяСНачала() {
        // Хвост важнее начала: ошибки и итог сборки печатаются последними.
        String many =
                System.getProperty("os.name").toLowerCase().contains("win")
                        // Двойной процент: команда уезжает в оболочку файлом-скриптом,
                        // а в batch-файле переменная цикла пишется как %%i.
                        ? "for /l %%i in (1,1,4000) do @echo"
                                + " ____________________________________________________________"
                        : "for i in $(seq 1 4000); do echo"
                                + " ____________________________________________________________;"
                                + " done";

        ToolResult result = run(many);

        assertThat(result.content().length()).isLessThan(BashTool.MAX_OUTPUT_CHARS + 200);
        assertThat(result.content()).contains("начало вывода обрезано");
    }

    @Test
    void пустаяКомандаЭтоОшибка() {
        assertThat(run("   ").error()).isTrue();
    }

    @Test
    void читающиеКомандыНеТребуютПодтверждения() {
        assertThat(behaviorFor("git status")).isEqualTo(PermissionBehavior.ALLOW);
        assertThat(behaviorFor("ls -la")).isEqualTo(PermissionBehavior.ALLOW);
        assertThat(behaviorFor("git log --oneline")).isEqualTo(PermissionBehavior.ALLOW);
    }

    @Test
    void деструктивныеКомандыОпознаются() {
        assertThat(behaviorFor("rm -rf /tmp/x")).isEqualTo(PermissionBehavior.ASK);
        assertThat(behaviorFor("git reset --hard HEAD~1")).isEqualTo(PermissionBehavior.ASK);
        assertThat(behaviorFor("git push --force")).isEqualTo(PermissionBehavior.ASK);
    }

    @Test
    void деструктивныеОтличаютсяПричиной() {
        // Причину увидит пользователь в запросе подтверждения — «нельзя, потому что»
        // работает лучше, чем просто «нельзя».
        assertThat(
                        tool.checkPermissions(
                                        new BashTool.Input("rm -rf build", null, null), context)
                                .reason())
                .contains("необратимая операция");
        assertThat(
                        tool.checkPermissions(
                                        new BashTool.Input("mvnw package", null, null), context)
                                .reason())
                .contains("выполнение команды");
    }

    @Test
    void инструментМеняетСостояниеИЗовётсяBash() {
        assertThat(tool.isReadOnly()).isFalse();
        assertThat(tool.name()).isEqualTo("Bash");
        assertThat(tool.getClass().getSimpleName()).isEqualTo("BashTool");
    }

    @Test
    void таймаутПроверяетсяДоЗапуска() {
        assertThat(tool.call(new BashTool.Input("echo ok", null, 0), context).error()).isTrue();
        assertThat(tool.call(new BashTool.Input("echo ok", null, 601), context).error()).isTrue();
    }

    @Test
    void похожаяНаЧитающуюКомандаНеРазрешаетсяАвтоматически() {
        assertThat(behaviorFor("git branch new-branch")).isEqualTo(PermissionBehavior.ASK);
        assertThat(behaviorFor("git log --output=result.txt")).isEqualTo(PermissionBehavior.ASK);
        assertThat(behaviorFor("git status && echo hello")).isEqualTo(PermissionBehavior.ASK);
    }
}
