package ru.harness.agent;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Подставная модель со сценарием: отдаёт заранее заданные ответы по очереди
 * и запоминает, какие промпты получила.
 *
 * <p>Это главный инструмент тестирования агента во всём курсе. Настоящая LLM
 * недетерминирована и медленна — цикл агента на ней не протестируешь. Здесь же
 * мы задаём «модель ответила вызовом инструмента, потом текстом» и проверяем,
 * что цикл отработал ровно так, как задумано.
 */
public class ScriptedChatModel implements ChatModel {

    private final Deque<AssistantMessage> script = new ArrayDeque<>();
    private final List<Prompt> receivedPrompts = new ArrayList<>();

    public static ScriptedChatModel replyingWith(String... texts) {
        ScriptedChatModel model = new ScriptedChatModel();
        for (String text : texts) {
            model.script.add(new AssistantMessage(text));
        }
        return model;
    }

    public ScriptedChatModel then(AssistantMessage message) {
        script.add(message);
        return this;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        receivedPrompts.add(prompt);
        AssistantMessage next = script.poll();
        if (next == null) {
            throw new IllegalStateException(
                    "Сценарий исчерпан: агент сделал больше вызовов модели, чем ожидалось (%d)"
                            .formatted(receivedPrompts.size()));
        }
        return new ChatResponse(List.of(new Generation(next)));
    }

    /** Промпты в том порядке, в котором их получила модель. */
    public List<Prompt> receivedPrompts() {
        return List.copyOf(receivedPrompts);
    }

    public Prompt lastPrompt() {
        return receivedPrompts.getLast();
    }

    public int callCount() {
        return receivedPrompts.size();
    }
}
