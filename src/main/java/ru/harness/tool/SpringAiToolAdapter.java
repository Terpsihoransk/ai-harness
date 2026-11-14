package ru.harness.tool;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SpringAiToolAdapter {

    private final ToolRegistry registry;

    public SpringAiToolAdapter(ToolRegistry registry) {
        this.registry = registry;
    }


    public List<ToolCallback> toolCallbacks() {
        return registry.tools().stream()
                .map(SpringAiToolAdapter::describe)
                .toList();
    }

    private static ToolCallback describe(Tool<?> tool) {
        ToolDefinition toolDefinition = ToolDefinition.builder()
                .name(tool.name())
                .description(tool.description())
                .inputSchema(tool.inputSchema())
                .build();
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return toolDefinition;
            }

            @Override
            public String call(String toolInput) {
                throw new IllegalStateException(
                        "инструменты выполняет ToolRegistry, а не Spring AI: " + tool.name());

            }
        };
    }
}
