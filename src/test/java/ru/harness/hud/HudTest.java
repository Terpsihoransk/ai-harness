package ru.harness.hud;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.messages.AssistantMessage;
import ru.harness.TestPermissionConfig;
import ru.harness.agent.Agent;
import ru.harness.agent.AgentProperties;
import ru.harness.agent.Conversation;
import ru.harness.agent.event.AgentEvent;
import ru.harness.permission.PermissionMode;
import ru.harness.permission.PermissionProperties;
import ru.harness.tool.WorkspaceGuard;
import ru.harness.tool.impl.BashTool;

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
    private Agent agent;

    /**
     * HUD тестируется отдельно от агента: на вход — события, на выход — текст. История настоящая,
     * агент — заглушка: монитору нужен только снимок разговора.
     */
    @BeforeEach
    void setUp() {
        buffer = new ByteArrayOutputStream();
        conversation = new Conversation("SYSTEM PROMPT");
        agent = Mockito.mock(Agent.class);
        Mockito.when(agent.conversation()).thenReturn(conversation);

        hud =
                new Hud(
                        new MachineViewPrinter(
                                new PrintStream(buffer, true, StandardCharsets.UTF_8)),
                        agent,
                        List.of(),
                        TestPermissionConfig.evaluator(
                                TestPermissionConfig.permissive()));
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

    @Test
    void запросРазрешенияПоказываетКомандуИПричину() {
        hud.onPermissionRequested(
                new AgentEvent.PermissionRequested("Bash", "build", "нужен запуск"));
        assertThat(output()).contains("ТРЕБУЕТСЯ АВТОРИЗАЦИЯ", "Bash", "build", "нужен запуск");
    }

    @Test
    void разрешениеПравкиРисуетМанипуляторы() {
        hud.onPermissionResolved(new AgentEvent.PermissionResolved("Edit", true));
        assertThat(output()).contains("МАНИПУЛЯТОРЫ", "ARMED").doesNotContain("ВООРУЖЕНИЕ");
    }

    @Test
    void отказНеПечатаетArmed() {
        hud.onPermissionResolved(new AgentEvent.PermissionResolved("Bash", false));
        assertThat(output()).contains("В ДОСТУПЕ ОТКАЗАНО").doesNotContain("ARMED");
    }

    @Test
    void вооружениеПомечаетсяLockedПоРежимуСервиса() {
        var properties = new AgentProperties(20, ".", true);
        var permissions =
                TestPermissionConfig.evaluator(
                        new PermissionProperties(
                                PermissionMode.ACCEPT_EDITS,
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of()));
        Hud monitor =
                new Hud(
                        new MachineViewPrinter(
                                new PrintStream(buffer, true, StandardCharsets.UTF_8)),
                        agent,
                        List.of(new BashTool(new WorkspaceGuard(properties))),
                        permissions);
        List<BootLine> lines =
                org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                        monitor, "collectBootLines", 160);
        assertThat(lines)
                .filteredOn(line -> line.part() == BodyPart.ARMAMENT)
                .singleElement()
                .extracting(BootLine::status)
                .isEqualTo(BootStatus.LOCKED);
    }

    @Test
    void bypassСнимаетОбычнуюБлокировкуВBootЛоге() {
        var properties = new AgentProperties(20, ".", true);
        Hud monitor =
                new Hud(
                        new MachineViewPrinter(
                                new PrintStream(buffer, true, StandardCharsets.UTF_8)), agent,
                        List.of(new BashTool(new WorkspaceGuard(properties))),
                        TestPermissionConfig.evaluator(
                                TestPermissionConfig.permissive()));
        List<BootLine> lines =
                org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                        monitor, "collectBootLines", 160);
        assertThat(lines)
                .filteredOn(line -> line.part() == BodyPart.ARMAMENT)
                .singleElement()
                .extracting(BootLine::status)
                .isEqualTo(BootStatus.ONLINE);
    }

}
