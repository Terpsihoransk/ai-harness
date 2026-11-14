package ru.harness.agent;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentTest {
    private final RecordingEventPublisher events = new RecordingEventPublisher();

    @Test
    void возвращаетОтветМодели() {
        Agent agent = TestAgents.agent(ScriptedChatModel.replyingWith("I am Harness."), events);

        assertThat(agent.run("Кто ты?")).isEqualTo("I am Harness.");
    }

    @Test
    void первымСообщениемВсегдаИдётСистемныйПромпт() {
        ScriptedChatModel model = ScriptedChatModel.replyingWith("ok");
        Agent agent = TestAgents.agent(model, events);

        agent.run("Привет");

        List<Message> sent = model.lastPrompt().getInstructions();
        assertThat(sent.getFirst().getMessageType()).isEqualTo(MessageType.SYSTEM);
        assertThat(sent.getFirst().getText()).contains("Harness");
    }

    @Test
    void помнитПредыдущиеХоды() {
        ScriptedChatModel model = ScriptedChatModel.replyingWith("Привет!", "Тебя зовут Саша.");
        Agent agent = TestAgents.agent(model, events);

        agent.run("Меня зовут Саша");
        agent.run("Как меня зовут?");

        // Второй запрос содержит всю историю: у модели нет памяти между вызовами,
        // помнит за неё Conversation.
        List<String> secondCall = model.lastPrompt().getInstructions().stream().map(Message::getText).toList();
        assertThat(secondCall)
                .containsExactly(
                        secondCall.getFirst(), "Меня зовут Саша", "Привет!", "Как меня зовут?");
    }
}
