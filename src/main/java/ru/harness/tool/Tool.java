package ru.harness.tool;

import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import ru.harness.permission.PermissionBehavior;
import ru.harness.permission.PermissionDecision;
import ru.harness.permission.PermissionRule;
import ru.harness.permission.PermissionSource;
import ru.harness.permission.PermissionUpdate;

import java.util.List;
import java.util.Locale;

public interface Tool<I> {

    String name();

    String description();

    Class<I> inputType();

    ToolResult call(I input, ToolContext context);

    default boolean isReadOnly() {
        return true;
    }

    default String renderToolUseMessage(I input) {
        return tag();
    }

    default String tag() {
        return "[" + name().toUpperCase(Locale.ROOT) + "]";
    }

    default String inputSchema() {
        return JsonSchemaGenerator.generateForType(inputType());
    }

    /**
     * PermissionEvaluator вызывает этот метод перед исполнением. По умолчанию чтение разрешено, изменение — обычный ASK
     * с предложением больше не спрашивать для этого инструмента. Bash и файловые инструменты проверяют свои аргументы.
     * Запрет на конкретный файл или команду возвращаем как DENY: Service вернёт его раньше общего ASK на инструмент.
     */
    default PermissionDecision checkPermissions(I input, ToolContext context) {
        if (isReadOnly()) return PermissionDecision.allow("инструмент только читает");
        return PermissionDecision.ask("инструмент меняет состояние")
                .withSuggestions(List.of(PermissionUpdate.rule(new PermissionRule(name(), null), PermissionBehavior.ALLOW, PermissionSource.LOCAL)));
    }

}
