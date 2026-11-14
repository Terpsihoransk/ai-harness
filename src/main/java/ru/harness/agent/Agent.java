package ru.harness.agent;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import ru.harness.agent.event.AgentEvent;
import ru.harness.context.ContextManager;
import ru.harness.tool.SpringAiToolAdapter;
import ru.harness.tool.ToolRegistry;
import ru.harness.tool.ToolResult;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class Agent {

    private final ApplicationEventPublisher publisher;
    private final Conversation conversation;
    private final ToolRegistry toolRegistry;
    private final SpringAiToolAdapter springAiToolAdapter;
    private final AgentProperties agentProperties;

    public Agent(ChatModel chatModel,
                 ApplicationEventPublisher publisher,
                 ToolRegistry toolRegistry,
                 SpringAiToolAdapter springAiToolAdapter,
                 AgentProperties agentProperties,
                 ContextManager contextManager) {
        this.chatModel = chatModel;
        this.publisher = publisher;
        this.toolRegistry = toolRegistry;
        this.springAiToolAdapter = springAiToolAdapter;
        this.agentProperties = agentProperties;
        this.conversation = new Conversation(contextManager.systemPrompt());
    }

    public Conversation conversation() {
        return conversation;
    }

    private final ChatModel chatModel;


    @Nullable
    public String chatModel() {

        return chatModel.getOptions().getModel();
    }

    public String run(String userMessage) {
        Instant startTime = Instant.now();
        conversation.addUserMessage(userMessage);
        publisher.publishEvent(new AgentEvent.TurnStarted(userMessage));
        for (int iteration = 1; iteration <= agentProperties.maxTurns(); iteration++) {
            AssistantMessage assistantMessage = askModel();
            conversation.addAssistantMessage(assistantMessage);
            publisher.publishEvent(new AgentEvent.AgentSpoke(assistantMessage.getText()));
            if (!assistantMessage.hasToolCalls()) {
                publisher.publishEvent(new AgentEvent.TurnFinished(Duration.between(startTime, Instant.now()), iteration));
                return assistantMessage.getText();
            }
            conversation.addToolResponses(executeAll(assistantMessage));
        }
        publisher.publishEvent(new AgentEvent.TurnLimitReached(agentProperties.maxTurns()));
        publisher.publishEvent(new AgentEvent.TurnFinished(Duration.between(startTime, Instant.now()), agentProperties.maxTurns()));
        return "Достигнут предел в %d итераций — задача не завершена.".formatted(agentProperties.maxTurns());

    }

    private AssistantMessage askModel() {
        Prompt prompt = Prompt.builder()
                .messages(conversation.toPromptMessages())
                .chatOptions(toolOptions())
                .build();

        return chatModel.call(prompt).getResult().getOutput();
    }

    private ToolResponseMessage executeAll(AssistantMessage assistantMessage) {
        List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();
        for (AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {
            ToolResult toolResult = toolRegistry.execute(toolCall.name(), toolCall.arguments());
            toolResponses.add(new ToolResponseMessage.ToolResponse(toolCall.id(), toolCall.name(), toolResult.content()));
        }
        return ToolResponseMessage.builder().responses(toolResponses).build();
    }

    private ChatOptions toolOptions() {
        return chatModel
                .getOptions()
                .mutate()
                .combineWith(
                        ToolCallingChatOptions
                                .builder()
                                .toolCallbacks((springAiToolAdapter.toolCallbacks()))
                )
                .build();
    }

}