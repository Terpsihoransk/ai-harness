package ru.harness.permission;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.harness.TestPermissionConfig;
import ru.harness.agent.AgentProperties;
import ru.harness.tool.FileStateCache;
import ru.harness.tool.WorkspaceGuard;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PathInspectorTest {
    @TempDir Path root;

    private WorkspaceGuard guard() {
        return new WorkspaceGuard(new AgentProperties(20, root.toString(), true));
    }

    private void directoryLink(Path link, Path target) throws Exception {
        try {
            Files.createSymbolicLink(link, target);
        } catch (java.io.IOException | UnsupportedOperationException noSymlinks) {
            // Junction проверяет тот же выход наружу на Windows и не требует права создавать symlink.
            assumeTrue(System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win"), "ОС не разрешает создать ссылку на каталог");
            Process junction = new ProcessBuilder("cmd.exe", "/c", "mklink", "/J", link.toString(), target.toString()).redirectErrorStream(true).start();
            String output = new String(junction.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assumeTrue(junction.waitFor() == 0, "Не удалось создать junction: " + output);
        }
    }

    private TestPermissionConfig.Setup setup(List<String> deny) {
        return TestPermissionConfig.setup(
                TestPermissionConfig.properties(PermissionMode.DEFAULT, List.of(), deny, List.of()),
                guard(),
                new FileStateCache());
    }

    @Test
    void предлагаемШиреОдногоФайлаКаталогАНеРежим() {
        var permissions = ToolPermissionContext.empty();
        String outside = "//" + guard().resolve("../shared").toString().replace('\\', '/') + "/**";
        // Запись снаружи: правило Edit на каталог и сам каталог в рабочую область, оба на сессию.
        assertThat(PathInspector.checkWrite(guard(), "../shared/App.java", permissions).suggestions())
                .containsExactly(
                        PermissionUpdate.rule(new PermissionRule("Edit", outside), PermissionBehavior.ALLOW, PermissionSource.SESSION),
                        PermissionUpdate.directory(guard().resolve("../shared").toString()));
        // Чтение снаружи: правило Read на весь каталог на сессию.
        assertThat(PathInspector.checkRead(guard(), "../shared/App.java", permissions).suggestions())
                .containsExactly(PermissionUpdate.rule(
                        new PermissionRule("Read", outside),
                        PermissionBehavior.ALLOW, PermissionSource.SESSION));
        // Запись внутри проекта: правило Edit на каталог файла, рабочую область расширять не нужно.
        String inside = "//" + guard().root().resolve("src").toString().replace('\\', '/') + "/**";
        assertThat(PathInspector.checkWrite(guard(), "src/App.java", permissions).suggestions())
                .containsExactly(PermissionUpdate.rule(
                        new PermissionRule("Edit", inside),
                        PermissionBehavior.ALLOW, PermissionSource.SESSION));
    }

    @Test
    void согласиеНаКаталогСнимаетВопросТолькоВНём() {
        var s = setup(List.of());
        var asked = PathInspector.checkWrite(guard(), "src/App.java", s.context().permissions());
        assertThat(asked.behavior()).isEqualTo(PermissionBehavior.ASK);
        asked.suggestions().forEach(s.orders()::apply);
        var permissions = s.context().permissions();
        assertThat(PathInspector.checkWrite(guard(), "src/Other.java", permissions).behavior())
                .isEqualTo(PermissionBehavior.ALLOW);
        // Соседний каталог по-прежнему спрашивает: согласие не шире того, о чём спросили.
        assertThat(PathInspector.checkWrite(guard(), "docs/README.md", permissions).behavior())
                .isEqualTo(PermissionBehavior.ASK);
    }

    @Test
    void новыйФайлЧерезSymlinkНельзяАвтоматическиСчитатьВнутренним() throws Exception {
        Path outside = Files.createTempDirectory("harness-permission-outside-");
        try {
            directoryLink(root.resolve("link"), outside);
            var s = TestPermissionConfig.setup(
                    TestPermissionConfig.properties(PermissionMode.ACCEPT_EDITS, List.of(), List.of(), List.of()),
                    guard(),
                    new FileStateCache());
            var decision = PathInspector.checkWrite(guard(), "link/new.txt", s.context().permissions());
            assertThat(decision.behavior()).isEqualTo(PermissionBehavior.ASK);
            assertThat(outside.resolve("new.txt")).doesNotExist();
        } finally {
            Files.deleteIfExists(root.resolve("link"));
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void запретРеальногоПутиРаботаетЧерезРазрешённыйAlias() throws Exception {
        Files.createDirectory(root.resolve("private"));
        Files.writeString(root.resolve("private/key.txt"), "secret");
        try {
            directoryLink(root.resolve("alias"), root.resolve("private"));
            var decision = PathInspector.checkRead(guard(), "alias/key.txt", setup(List.of("Read(private/**)")).context().permissions());
            assertThat(decision.behavior()).isEqualTo(PermissionBehavior.DENY);
        } finally {
            Files.deleteIfExists(root.resolve("alias"));
        }
    }
}
