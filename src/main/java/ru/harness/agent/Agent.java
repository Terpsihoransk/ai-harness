package ru.harness.agent;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import ru.harness.agent.event.AgentEvent;

import java.time.Duration;
import java.time.Instant;

@Service
public class Agent {

    private static final String SYSTEM_PROMPT = """
            You are Harness, a coding agent that works in a local repository.
            Answer concisely.""";
    private final ApplicationEventPublisher publisher;

    private final Conversation conversation = new Conversation(SYSTEM_PROMPT);

    public Conversation conversation() {
        return conversation;
    }

    private final ChatModel chatModel;


    @Nullable
    public String chatModel() {
        return chatModel.getOptions().getModel();
    }

    public Agent(ChatModel chatModel, ApplicationEventPublisher publisher) {
        this.chatModel = chatModel;
        this.publisher = publisher;
    }

    public String run(String userMessage) {
        Instant startTime = Instant.now();

        conversation.addUserMessage(userMessage);
        // После того как положили сообщение пользователя - кидаем евент
        publisher.publishEvent(new AgentEvent.TurnStarted(userMessage));
        AssistantMessage assistantMessage = chatModel.call(new Prompt(conversation.toPromptMessages())).getResult().getOutput();
        conversation.addAssistantMessage(assistantMessage);

        // Проверяем то, что получилось и замеряем время
        publisher.publishEvent(new AgentEvent.AgentSpoke(assistantMessage.getText()));
        publisher.publishEvent(new AgentEvent.TurnFinished(Duration.between(startTime, Instant.now())));

        return assistantMessage.getText();
    }

}
