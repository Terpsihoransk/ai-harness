package ru.harness.tool;

import org.springframework.ai.util.json.schema.JsonSchemaGenerator;

public interface Tool<I> {
    String name();
    String description();
    Class<I> inputType();
    ToolResult call(I input);
    default boolean isReadOnly() { return true; }
    default String renderToolUseMessage(I input) { return ""; }
    default String inputSchema() { return JsonSchemaGenerator.generateForType(inputType()); }
}
