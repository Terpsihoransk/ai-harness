package ru.harness.tool;

import org.springframework.ai.util.json.schema.JsonSchemaGenerator;

import java.util.Locale;

public interface Tool<I> {

    String name();
    String description();
    Class<I> inputType();
    ToolResult call(I input, ToolContext context);
    default boolean isReadOnly() { return true; }
    default String renderToolUseMessage(I input) { return tag(); }
    default String tag() { return "[" + name().toUpperCase(Locale.ROOT) + "]"; }
    default String inputSchema() { return JsonSchemaGenerator.generateForType(inputType()); }

}
