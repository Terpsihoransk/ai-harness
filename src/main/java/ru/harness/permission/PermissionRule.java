package ru.harness.permission;

import org.jspecify.annotations.Nullable;

/**
 * Правило из allow, deny или ask: имя инструмента и необязательный шаблон аргумента. Примеры
 * разбора:
 *
 * <pre>
 * parse("Bash")            → toolName="Bash", argumentPattern=null
 * parse("Bash(git push*)") → toolName="Bash", argumentPattern="git push*"
 * parse("Edit(src/**)")    → toolName="Edit", argumentPattern="src/**"
 * parse("  Read( *.md ) ") → toolName="Read", argumentPattern="*.md"
 * parse("Bash()")          → toolName="Bash", argumentPattern=null
 * parse("Bash(git push")   → IllegalArgumentException: не закрыта скобка
 * </pre>
 *
 * PermissionEvaluator проверяет имя целого инструмента. PathInspector и CommandInspector проверяют
 * шаблон. Без шаблона правило относится ко всему инструменту. Если совпало правило из deny —
 * отказываем; из allow — снимаем обычный вопрос.
 */
public record PermissionRule(String toolName, @Nullable String argumentPattern) {

    public static PermissionRule parse(String raw) {
        // Убираем внешние пробелы, например перед Bash(git status).
        String text = raw.strip();
        // Пустая строка не может назвать инструмент.
        if (text.isEmpty()) {
            throw new IllegalArgumentException("не указан инструмент в правиле: " + raw);
        }
        // Первая скобка отделяет имя инструмента от его аргумента-шаблона.
        int open = text.indexOf('(');
        // Без скобок получаем правило на весь Tool, например Bash.
        if (open < 0) {
            // null означает отсутствие уточнения: правило относится ко всем аргументам этого Tool.
            return new PermissionRule(text, null);
        }
        // Если открыли скобку, требуем закрывающую скобку в конце правила.
        if (!text.endsWith(")")) {
            throw new IllegalArgumentException("не закрыта скобка в правиле: " + raw);
        }
        // Берём имя до скобки: из Edit(src/**) получится Edit.
        String tool = text.substring(0, open).strip();
        // Берём только содержимое скобок: из Edit(src/**) получится src/**.
        String pattern = text.substring(open + 1, text.length() - 1).strip();
        // Запись вроде (src/**) не сообщает, к какому Tool относится правило.
        if (tool.isEmpty()) {
            throw new IllegalArgumentException("не указан инструмент в правиле: " + raw);
        }
        // Пустые скобки равнозначны отсутствию шаблона: Bash() означает весь Bash.
        return new PermissionRule(tool, pattern.isEmpty() ? null : pattern);
    }

    /** Сравниваем только имя: Edit и edit — true; Edit и Bash — false. */
    public boolean appliesTo(String tool) {
        // Сравниваем имя без регистра; шаблон аргумента здесь ещё не проверяется.
        return toolName.equalsIgnoreCase(tool);
    }

    @Override
    public String toString() {
        // Восстанавливаем строку для сообщения человеку или записи в YAML.
        return argumentPattern == null ? toolName : "%s(%s)".formatted(toolName, argumentPattern);
    }
}