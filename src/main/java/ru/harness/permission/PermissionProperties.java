package ru.harness.permission;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Spring читает начальные настройки. OrdersCollector добавляет YAML-файлы пользователя и
 * проекта.
 *
 * <pre>
 * harness.permission:
 *   mode: DEFAULT
 *   allow: ['Bash(git status)']
 *   deny: ['Read(.env)']
 *   ask: ['Edit(src/security/**)']
 *   additional-directories: ['../shared']
 * </pre>
 */
@ConfigurationProperties("harness.permission")
public record PermissionProperties(
        // Режим по умолчанию, если application.yaml не задал свой.
        @DefaultValue("ACCEPT_EDITS") PermissionMode mode,
        // Строки разрешающих правил: например Bash(git status).
        @DefaultValue({}) List<String> allow,
        // Запреты: например Read(.env). Они сильнее разрешений любого источника.
        @DefaultValue({}) List<String> deny,
        // Явные обязательные вопросы: например Edit(src/security/**).
        @DefaultValue({}) List<String> ask,
        // Дополнительные рабочие каталоги; это не ALLOW для всех Bash-команд.
        @DefaultValue({}) List<String> additionalDirectories) {}