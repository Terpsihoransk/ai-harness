package ru.harness.permission;

import ru.harness.TestPermissionConfig;
import ru.harness.agent.AgentProperties;
import ru.harness.tool.WorkspaceGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrdersKeeperTest {
    @TempDir Path root;

    private WorkspaceGuard guard() {
        return new WorkspaceGuard(new AgentProperties(20, root.toString(), true));
    }

    private OrdersCollector collector() {
        return new OrdersCollector(
                TestPermissionConfig.properties(
                        PermissionMode.DEFAULT, List.of(), List.of(), List.of()),
                guard(),
                Map.of(
                        PermissionSource.USER,
                        root.resolve("user.yaml"),
                        PermissionSource.PROJECT,
                        root.resolve("project.yaml"),
                        PermissionSource.LOCAL,
                        root.resolve("local.yaml")));
    }

    private OrdersKeeper store() {
        return new OrdersKeeper(collector(), guard());
    }

    @Test
    void правилаВсехИсточниковСохраняютсяАРежимБерётсяИзПоследнего() throws Exception {
        Files.writeString(root.resolve("user.yaml"), "mode: DEFAULT\ndeny: ['Read(secret/**)']\n");
        Files.writeString(
                root.resolve("project.yaml"), "mode: ACCEPT_EDITS\nallow: ['Read(secret/**)']\n");
        var loaded = store().snapshot();
        assertThat(loaded.mode()).isEqualTo(PermissionMode.ACCEPT_EDITS);
        assertThat(loaded.ruleEntries())
                .extracting(PermissionEntry::behavior)
                .containsExactly(PermissionBehavior.DENY, PermissionBehavior.ALLOW);
    }

    @Test
    void сохранённоеПравилоЧитаетсяПослеНовогоЗапускаАСессионноеНет() {
        var store = store();
        store.apply(
                PermissionUpdate.rule(
                        PermissionRule.parse("Bash(./mvnw test*)"),
                        PermissionBehavior.ALLOW,
                        PermissionSource.LOCAL));
        store.apply(PermissionUpdate.rule(PermissionRule.parse("Bash(echo once)"), PermissionBehavior.ALLOW, PermissionSource.SESSION));
        assertThat(store.snapshot().ruleEntries()).hasSize(2);
        assertThat(store().snapshot().ruleEntries())
                .singleElement()
                .satisfies(e -> assertThat(e.rule().argumentPattern()).isEqualTo("./mvnw test*"));
    }

    @Test
    void правкаФайлаВступаетВСилуПриЗапускеАСогласиеСразу() throws Exception {
        var store = store();
        Files.writeString(root.resolve("local.yaml"), "mode: BYPASS_PERMISSIONS\nask: ['Edit(src/**)']\n");
        store.apply(PermissionUpdate.rule(PermissionRule.parse("Bash(echo ok)"), PermissionBehavior.ALLOW, PermissionSource.LOCAL));
        // Режим прочитан при старте и в работе не меняется: правка файла его не подменяет.
        assertThat(store.snapshot().mode()).isEqualTo(PermissionMode.DEFAULT);
        assertThat(store.snapshot().ruleEntries())
                .singleElement()
                .satisfies(e -> assertThat(e.behavior()).isEqualTo(PermissionBehavior.ALLOW));
        var afterRestart = store().snapshot();
        assertThat(afterRestart.mode()).isEqualTo(PermissionMode.BYPASS_PERMISSIONS);
        assertThat(afterRestart.ruleEntries()).extracting(PermissionEntry::behavior)
                .containsExactlyInAnyOrder(PermissionBehavior.ASK, PermissionBehavior.ALLOW);
    }

    @Test
    void сбойЗаписиНеПубликуетПостоянноеСогласие() throws Exception {
        var store = store();
        store.apply(PermissionUpdate.rule(PermissionRule.parse("Bash(echo ok)"), PermissionBehavior.ALLOW, PermissionSource.SESSION));
        Files.writeString(root.resolve("local.yaml"), "deny: [broken\n");
        assertThatThrownBy(() -> store.apply(PermissionUpdate.rule(
                PermissionRule.parse("Bash(echo local)"), PermissionBehavior.ALLOW, PermissionSource.LOCAL)))
                .isInstanceOf(org.yaml.snakeyaml.error.YAMLException.class);
        assertThat(store.snapshot().ruleEntries())
                .singleElement()
                .satisfies(e -> assertThat(e.source()).isEqualTo(PermissionSource.SESSION));
    }
}
