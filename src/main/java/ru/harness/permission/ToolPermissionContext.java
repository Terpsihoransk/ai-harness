package ru.harness.permission;

import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

/**
 * Неизменяемые данные разрешений: mode, объединённые ruleEntries и дополнительные рабочие каталоги.
 * OrdersKeeper хранит текущий снимок и заменяет его после согласия человека.
 * PermissionEvaluator и помощники Tool читают этот record; сам он решений не принимает.
 */
public record ToolPermissionContext(PermissionMode mode, List<PermissionEntry> ruleEntries, List<Path> additionalWorkingDirectories) {
    public ToolPermissionContext {
        // Список правил этого снимка нельзя менять; новый набор публикуем новым контекстом.
        ruleEntries = List.copyOf(ruleEntries);
        // Так же фиксируем рабочие каталоги, чтобы читатель не увидел их изменение посреди проверки.
        additionalWorkingDirectories = List.copyOf(additionalWorkingDirectories);
    }

    public static ToolPermissionContext empty() {
        // Пустой контекст удобен тестам; рабочее приложение начинает с приказов из OrdersCollector.
        return new ToolPermissionContext(PermissionMode.DEFAULT, List.of(), List.of());
    }

    /**
     * Правило без шаблона на весь инструмент: Bash, но не Bash(git *). null — такого нет.
     * Одно место для проверки вызова и для решения, показывать ли Tool модели.
     */
    public @Nullable PermissionEntry wholeToolRule(String toolName, PermissionBehavior behavior) {
        for (PermissionEntry ruleEntry : ruleEntries) {
            if (ruleEntry.behavior() == behavior && ruleEntry.rule().argumentPattern() == null && ruleEntry.rule().appliesTo(toolName)) {
                return ruleEntry;
            }
        }
        return null;
    }

}