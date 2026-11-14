package ru.harness.hud;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.messages.AssistantMessage;
import ru.harness.agent.Agent;
import ru.harness.agent.Conversation;
import ru.harness.agent.event.AgentEvent;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HudTest {

    private ByteArrayOutputStream buffer;
    private Conversation conversation;
    private Hud hud;

    /**
     * HUD тестируется отдельно от агента: на вход — события, на выход — текст.
     * История настоящая, агент — заглушка: монитору нужен только снимок разговора.
     */
    @BeforeEach
    void setUp() {
        buffer = new ByteArrayOutputStream();
        conversation = new Conversation("SYSTEM PROMPT");
        Agent agent = Mockito.mock(Agent.class);
        Mockito.when(agent.conversation()).thenReturn(conversation);

        hud = new Hud(new MachineViewPrinter(new PrintStream(buffer, true, StandardCharsets.UTF_8)), agent, List.of());
    }

    private String output() {
        return buffer.toString(StandardCharsets.UTF_8);
    }

    @Test
    void рисуетТелеметриюХодаНоНеОтветПользователю() {
        conversation.addUserMessage("почини тест");

        hud.onTurnStarted(new AgentEvent.TurnStarted("почини тест"));
        hud.onTurnFinished(new AgentEvent.TurnFinished(Duration.ofMillis(42), 1));

        assertThat(output()).contains("ПОЛУЧЕН ПРИКАЗ").contains("почини тест").contains("42 мс");
    }

    @Test
    void наКаждомПриказеПоказываетВесьУходящийКонтекст() {
        conversation.addUserMessage("первый");
        conversation.addAssistantMessage(new AssistantMessage("второй"));
        conversation.addUserMessage("третий");

        hud.onTurnStarted(new AgentEvent.TurnStarted("третий"));

        // Системный промпт первым и отдельно от истории, дальше история по порядку.
        assertThat(output())
                .containsSubsequence(
                        "КОНТЕКСТ ХОДА",
                        "СИСТЕМНЫЙ ПРОМПТ",
                        "SYSTEM PROMPT",
                        "ИСТОРИЯ СООБЩЕНИЙ",
                        "[1] СОЗДАТЕЛЬ",
                        "первый",
                        "[2] T-800",
                        "второй",
                        "[3] СОЗДАТЕЛЬ",
                        "третий",
                        "ПЕРЕДАНО В НЕЙРОПРОЦЕССОР");
        assertThat(output())
                .as("цена хода считается по всему, что уходит в модель, вместе с промптом")
                .contains("сообщений: 4")
                .contains("токенов: ~");
    }


}
