package ru.harness;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import ru.harness.agent.AgentProperties;
import ru.harness.permission.OrdersCollector;
import ru.harness.permission.OrdersKeeper;
import ru.harness.permission.PermissionEvaluator;
import ru.harness.permission.PermissionGate;
import ru.harness.permission.PermissionMode;
import ru.harness.permission.PermissionPrompt;
import ru.harness.permission.PermissionProperties;
import ru.harness.permission.PermissionSource;
import ru.harness.tool.FileStateCache;
import ru.harness.tool.Tool;
import ru.harness.tool.ToolContext;
import ru.harness.tool.ToolRegistry;
import ru.harness.tool.WorkspaceGuard;

import java.util.List;
import java.util.Map;

/**
 * Ручная сборка той же цепочки для тестов, без чтения личных настроек преподавателя.
 */
@TestConfiguration
public class TestPermissionConfig {

    @Bean
    PermissionPrompt permissionPrompt() {
        return allowOnce();
    }

    public static PermissionPrompt allowOnce() {
        return (tool, target, reason) -> PermissionPrompt.Answer.ALLOW_ONCE;
    }

    public static PermissionProperties permissive() {
        return properties(PermissionMode.BYPASS_PERMISSIONS, List.of(), List.of(), List.of());
    }

    public static PermissionProperties properties(
            PermissionMode mode, List<String> allow, List<String> deny, List<String> ask) {
        return new PermissionProperties(mode, allow, deny, ask, List.of());
    }

    public static ToolContext context(FileStateCache cache) {
        var guard = new WorkspaceGuard(new AgentProperties(20, ".", true));
        var collector = new OrdersCollector(
                properties(PermissionMode.DEFAULT, List.of(), List.of(), List.of()), guard, Map.of());
        return new ToolContext(cache, new OrdersKeeper(collector, guard));
    }

    public record Setup(
            ToolContext context, PermissionEvaluator evaluator, OrdersKeeper orders) {}

    public static Setup setup(PermissionProperties p, WorkspaceGuard guard, FileStateCache cache) {
        // Свои временные файлы user/project/local: «запомнить» пишет в local, личные настройки не читаем и не трогаем.
        var collector = new OrdersCollector(p, guard, temporaryOrdersFiles());
        var orders = new OrdersKeeper(collector, guard);
        return new Setup(
                new ToolContext(cache, orders),
                new PermissionEvaluator(orders),
                orders);
    }

    private static Map<PermissionSource, java.nio.file.Path> temporaryOrdersFiles() {
        try {
            var directory = java.nio.file.Files.createTempDirectory("harness-permissions-");
            directory.toFile().deleteOnExit();
            Map<PermissionSource, java.nio.file.Path> files = new java.util.HashMap<>();
            for (PermissionSource source : List.of(PermissionSource.USER, PermissionSource.PROJECT, PermissionSource.LOCAL)) {
                var file = directory.resolve(source + ".yaml");
                file.toFile().deleteOnExit();
                files.put(source, file);
            }
            return files;
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    public static PermissionEvaluator evaluator(PermissionProperties p) {
        return setup(
                p,
                new WorkspaceGuard(new AgentProperties(20, ".", true)),
                new FileStateCache())
                .evaluator();
    }

    public static ToolRegistry registry(
            List<Tool<?>> tools,
            ApplicationEventPublisher events,
            FileStateCache cache,
            PermissionProperties p,
            PermissionPrompt prompt) {
        var setup = setup(p, new WorkspaceGuard(new AgentProperties(20, ".", true)), cache);
        return new ToolRegistry(
                tools,
                events,
                setup.context(),
                new PermissionGate(setup.evaluator(), prompt, events));
    }

}
