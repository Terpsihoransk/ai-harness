package ru.harness.agent;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Хранит в себе историю разговора, системный промпт
 */
public class Conversation {

    private final String systemPrompt;
    private final List<Message> messages = new ArrayList<>();

    public Conversation(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public void addUserMessage(String userMessage) {
        messages.add(new UserMessage(userMessage));
    }

    public void addAssistantMessage(AssistantMessage assistantMessage) {
        messages.add(assistantMessage);
    }

    // Строит промпт - сначала системный, затем остальное в том же порядке
    public List<Message> toPromptMessages() {
        ArrayList<Message> prompt = new ArrayList<>(messages.size() + 1);
        prompt.add(new SystemMessage(systemPrompt));
        prompt.addAll(messages);
        return prompt;
    }

    public void addToolResponses(ToolResponseMessage toolResponseMessage) {
        messages.add(toolResponseMessage);
    }

}