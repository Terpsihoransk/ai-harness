package ru.harness.agent;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationTest {

    @Test
    void системныйПромптПопадаетВПромпт() {
        Conversation conversation = new Conversation("SYSTEM");
        conversation.addUserMessage("привет");

        assertThat(conversation.toPromptMessages()).hasSize(2);
    }

    @Test
    void собираетПромптВПравильномПорядке() {
        Conversation conversation = new Conversation("SYSTEM");
        conversation.addUserMessage("вопрос");
        conversation.addAssistantMessage(new AssistantMessage("ответ"));

        assertThat(conversation.toPromptMessages())
                .extracting(Message::getMessageType)
                .containsExactly(MessageType.SYSTEM, MessageType.USER, MessageType.ASSISTANT);
    }


}
