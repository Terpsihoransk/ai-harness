package ru.harness.agent;

import org.springframework.ai.chat.model.ChatModel;
import ru.harness.tool.FileStateCache;
import ru.harness.tool.SpringAiToolAdapter;
import ru.harness.tool.Tool;
import ru.harness.tool.ToolRegistry;

import java.util.List;

/** Сборка агента для тестов: без Spring-контекста и без сети. */
final class TestAgents {

    private TestAgents() {}

    static Agent agent(ChatModel model, RecordingEventPublisher events, Tool<?>... tools) {
        return agent(model, events, new AgentProperties(20, "."), tools);
    }

    static Agent agent(
            ChatModel model, RecordingEventPublisher events, AgentProperties properties, Tool<?>... tools) {
        ToolRegistry registry = new ToolRegistry(List.of(tools), events, new FileStateCache());
        return new Agent(model, events, registry, new SpringAiToolAdapter(registry), properties);
    }
}
