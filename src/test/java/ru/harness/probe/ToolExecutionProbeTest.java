package ru.harness.probe;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Разведка боем: проверяем, что в Spring AI 2.0 {@code ChatModel.call()} НЕ исполняет
 * инструменты сам, а возвращает намерение их вызвать.
 *
 * <p>В 1.x это регулировалось флагом {@code internalToolExecutionEnabled}. В 2.0 флаг
 * убран, а встроенный цикл выпилен из всех ChatModel. Проверяем это на живой модели,
 * потому что на этом факте стоит весь урок 3.
 */
@Tag("ollama")
@SpringBootTest
@ActiveProfiles("test")
class ToolExecutionProbeTest {

    @Autowired
    private OllamaChatModel chatModel;

    @Test
    void модельВозвращаетToolCallНоНеИсполняетЕго() {
        AtomicInteger executions = new AtomicInteger();

        ToolCallback weather = new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder()
                        .name("get_weather")
                        .description("Get the current weather for a city.")
                        .inputSchema(
                                """
                                {"type":"object","properties":{"city":{"type":"string"}},"required":["city"]}""")
                        .build();
            }

            @Override
            public String call(String toolInput) {
                executions.incrementAndGet();
                return "{\"temp\":\"-3C\"}";
            }
        };

        OllamaChatOptions options = OllamaChatOptions.builder()
                .model("qwen3.5:4b")
                .temperature(0.0)
                .toolCallbacks(List.of(weather))
                .build();

        ChatResponse response = chatModel.call(
                new Prompt(List.of(new UserMessage("What is the weather in Berlin? Use the tool.")), options));

        AssistantMessage answer = response.getResult().getOutput();

        System.out.println("=== hasToolCalls: " + answer.hasToolCalls());
        System.out.println("=== toolCalls:    " + answer.getToolCalls());
        System.out.println("=== text:         " + answer.getText());
        System.out.println("=== executions:   " + executions.get());

        assertThat(answer.hasToolCalls())
                .as("модель должна вернуть намерение вызвать инструмент")
                .isTrue();
        assertThat(executions)
                .as("Spring AI 2.0 не должен исполнять инструмент за нас — цикл наш")
                .hasValue(0);
    }
}