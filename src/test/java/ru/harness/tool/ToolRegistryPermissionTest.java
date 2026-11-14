package ru.harness.tool;

import ru.harness.TestPermissionConfig;
import ru.harness.agent.event.AgentEvent;
import ru.harness.permission.PermissionMode;
import ru.harness.permission.PermissionPrompt;
import ru.harness.permission.PermissionProperties;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ToolRegistryPermissionTest {

    private final List<AgentEvent> events = new ArrayList<>();
    private final AtomicInteger questions = new AtomicInteger();
    private final ProbeTool tool = new ProbeTool();

    static class ProbeTool implements Tool<String> {
        int calls;

        @Override
        public String name() {
            return "Probe";
        }

        @Override
        public String description() {
            return "test";
        }

        @Override
        public Class<String> inputType() {
            return String.class;
        }

        @Override
        public boolean isReadOnly() {
            return false;
        }

        @Override
        public String renderToolUseMessage(String input) {
            return input;
        }

        @Override
        public ToolResult call(String input, ToolContext context) {
            calls++;
            return ToolResult.ok("выполнено: " + input);
        }
    }

    private ToolRegistry registry(List<String> deny, PermissionPrompt.Answer answer) {
        PermissionProperties permissions =
                TestPermissionConfig.properties(
                        PermissionMode.DEFAULT, List.of(), deny, List.of());
        return TestPermissionConfig.registry(
                List.of(tool),
                event -> {
                    if (event instanceof AgentEvent agentEvent) events.add(agentEvent);
                },
                new FileStateCache(),
                permissions,
                (name, target, reason) -> {
                    questions.incrementAndGet();
                    return answer;
                });
    }

    @Test
    void запретНеИсполняетИнструментИНеЗадаётВопрос() {
        ToolResult result =
                registry(List.of("Probe"), PermissionPrompt.Answer.ALLOW_ONCE)
                        .execute("Probe", "\"one\"");
        assertThat(result.error()).isTrue();
        assertThat(result.content()).contains("запрещено правилом Probe");
        assertThat(tool.calls).isZero();
        assertThat(questions.get()).isZero();
    }

    @Test
    void отказЧеловекаВозвращаетсяМоделиБезИсполнения() {
        ToolResult result =
                registry(List.of(), PermissionPrompt.Answer.DENY).execute("Probe", "\"one\"");
        assertThat(result.error()).isTrue();
        assertThat(result.content()).contains("пользователь отказал");
        assertThat(tool.calls).isZero();
        assertThat(events)
                .anyMatch(
                        e ->
                                e instanceof AgentEvent.PermissionResolved resolved
                                        && !resolved.granted());
    }

    @Test
    void согласиеОдинРазИсполняетРовноОдинВызов() {
        ToolResult result =
                registry(List.of(), PermissionPrompt.Answer.ALLOW_ONCE).execute("Probe", "\"one\"");
        assertThat(result.error()).isFalse();
        assertThat(tool.calls).isEqualTo(1);
        assertThat(questions.get()).isEqualTo(1);
    }

    @Test
    void всегдаЗапоминаетИнструментБезСвоихПравил() {
        // У инструмента нет правил на аргументы, поэтому «больше не спрашивать» — правило на весь инструмент.
        ToolRegistry registry = registry(List.of(), PermissionPrompt.Answer.ALLOW_ALWAYS);
        registry.execute("Probe", "\"one\"");
        registry.execute("Probe", "\"one\"");
        registry.execute("Probe", "\"two\"");
        assertThat(questions.get()).isEqualTo(1);
        assertThat(tool.calls).isEqualTo(3);
    }

    @Test
    void разрешениеОдинРазНеЗапоминается() {
        ToolRegistry registry = registry(List.of(), PermissionPrompt.Answer.ALLOW_ONCE);
        registry.execute("Probe", "\"one\"");
        registry.execute("Probe", "\"one\"");
        assertThat(questions.get()).isEqualTo(2);
    }

    @Test
    void плохойJsonНеВызываетНиДиалогНиИнструмент() {
        ToolResult result =
                registry(List.of(), PermissionPrompt.Answer.ALLOW_ONCE).execute("Probe", "{");
        assertThat(result.error()).isTrue();
        assertThat(result.content()).contains("не разобрать аргументы");
        assertThat(tool.calls).isZero();
        assertThat(questions.get()).isZero();
    }

    @Test
    void событияОписываютПорядокДиалогаИИсполнения() {
        registry(List.of(), PermissionPrompt.Answer.ALLOW_ONCE).execute("Probe", "\"one\"");
        assertThat(events.stream().map(Object::getClass).toList())
                .containsExactly(
                        AgentEvent.ToolCallStarted.class, AgentEvent.PermissionRequested.class,
                        AgentEvent.PermissionResolved.class, AgentEvent.ToolCallFinished.class);
    }

    @Test
    void неизвестныйИнструментНеТребуетПодтверждения() {
        ToolResult result =
                registry(List.of(), PermissionPrompt.Answer.ALLOW_ONCE).execute("Missing", "{}");
        assertThat(result.error()).isTrue();
        assertThat(questions.get()).isZero();
        assertThat(events).isEmpty();
    }
}
