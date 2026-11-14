package ru.harness.tool;

import org.springframework.ai.util.JsonHelper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import ru.harness.agent.event.AgentEvent;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ToolRegistry {

    private final Map<String, Tool<?>> byName = new LinkedHashMap<>();
    private final ApplicationEventPublisher publisher;
    private final JsonHelper json = new JsonHelper();

    /** Сколько строк результата уезжает в событие — столько увидит человек. */
    private static final int PREVIEW_LINES = 3;

    /** Длинную строку обрезаем: в терминале она всё равно переносится. */
    private static final int PREVIEW_CHARS = 100;

    public ToolRegistry(List<Tool<?>> tools, ApplicationEventPublisher applicationEventPublisher) {
        for (Tool<?> tool : tools) {
            Tool<?> clash = byName.put(tool.name(), tool);
            if (clash != null) {
                throw new IllegalStateException(
                        "два инструмента с именем '%s': %s и %s"
                                .formatted(tool.name(), clash.getClass(), tool.getClass()));

            }
        }
        this.publisher = applicationEventPublisher;
    }

    public List<Tool<?>> tools() {
        return List.copyOf(byName.values());
    }

    public ToolResult execute(String toolName, String arguments) {
        Tool<?> tool = byName.get(toolName);
        if (tool == null) {
            return ToolResult.error(
                    "нет инструмента '%s'. Доступны: %s".formatted(toolName, byName.keySet()));
        }
        publisher.publishEvent(new AgentEvent.ToolCallStarted(toolName, describeCall(tool, arguments)));
        Instant toolStartedAt = Instant.now();
        ToolResult toolResult = invoke(tool, arguments);
        publisher.publishEvent(new AgentEvent.ToolCallFinished(
                toolName,
                toolResult.error(),
                toolResult.preview(PREVIEW_LINES, PREVIEW_CHARS),
                Duration.between(toolStartedAt, Instant.now())));


        return toolResult;
    }

    private <I> ToolResult invoke(Tool<I> tool, String argumentsJson) {
        I input;
        try {
            input = json.fromJson(argumentsJson, tool.inputType());
        } catch (Exception e) {
            return ToolResult.error("не разобрать аргументы %s: %s".formatted(tool.name(), e.getMessage()));
        }
        try {
            return tool.call(input);
        } catch (Exception e) {
            return ToolResult.error("%s упал: %s".formatted(tool.name(), e));
        }
    }

    private <I> String describeCall(Tool<I> tool, String argumentsJson) {
        try {
            String described = tool.renderToolUseMessage(json.fromJson(argumentsJson, tool.inputType()));
            return described == null || described.isBlank() ? tool.tag() : described;
        } catch (Exception argumentsAreBroken) {
            return tool.tag();
        }
    }

}
