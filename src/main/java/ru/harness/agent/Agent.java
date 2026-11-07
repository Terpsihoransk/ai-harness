package ru.harness.agent;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import ru.harness.Conversation;

@Service
public class Agent {

    private final Conversation conversation;
    private final ChatModel chatModel;


    private static final String SYSTEM_PROMPT = """
            Ты Харнесс, кодинг агент работает в локальном репозитории.
            Отвечай кратко.""";

    public Agent(ChatModel chatModel) {
        this.chatModel = chatModel;
        this.conversation = new Conversation(SYSTEM_PROMPT);
    }

    public Conversation conversation() {
        return conversation;
    }

    public String run(String userMessage) {
        conversation.addUserMessage(userMessage);
        AssistantMessage assistantMessage = chatModel.call(new Prompt(conversation.toPromptMessages())).getResult().getOutput();
        conversation.addAssistantMessage(assistantMessage);

        return assistantMessage.getText();
    }

}