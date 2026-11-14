package ru.harness.agent;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import ru.harness.tool.Tool;
import ru.harness.tool.ToolResult;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.harness.agent.TestAgents.agent;

/**
 * Тесты агентского цикла — сердце урока 3.
 *
 * <p>На живой модели такое не проверить: она недетерминирована и медленна.
 * {@link ScriptedChatModel} задаёт сценарий «первым ходом вызови инструмент,
 * вторым ответь текстом», и цикл становится обычным кодом, который проверяется
 * за миллисекунды.
 */
public class AgentLoopTest {

    private final RecordingEventPublisher publisher = new RecordingEventPublisher();

    /** Инструмент-шпион: записывает, с чем его позвали. */
    static class SpyTool implements Tool<SpyTool.Input> {
        record Input(String value) {}

        final List<String> calls = new ArrayList<>();
        private final ToolResult answer;

        SpyTool(ToolResult answer) {
            this.answer = answer;
        }

        @Override
        public String name() {
            return "Spy";
        }

        @Override
        public String description() {
            return "test tool";
        }

        @Override
        public Class<Input> inputType() {
            return Input.class;
        }

        @Override
        public String renderToolUseMessage(Input input) {
            return input.value();
        }

        @Override
        public ToolResult call(Input input) {
            calls.add(input.value());
            return answer;
        }
    }

    private static AssistantMessage toolCall(String id, String name, String argumentsJson) {
        return AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall(id, "function", name, argumentsJson)))
                .build();
    }

    private static ToolResponseMessage toolMessageOf(List<Message> request) {
        return (ToolResponseMessage) request.stream()
                .filter(message -> message.getMessageType() == MessageType.TOOL)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void выполняетИнструментИВозвращаетсяКМодели() {
        SpyTool tool = new SpyTool(ToolResult.ok("42"));
        ScriptedChatModel model = new ScriptedChatModel()
                .then(toolCall("call-1", "Spy", "{\"value\":\"pom.xml\"}"))
                .then(new AssistantMessage("Версия 25."));

        String answer = agent(model, publisher, tool).run("какая версия?");

        assertThat(tool.calls).containsExactly("pom.xml");
        assertThat(answer).isEqualTo("Версия 25.");
        assertThat(model.callCount()).as("два обращения к модели: до и после инструмента").isEqualTo(2);
    }

    @Test
    void результатИнструментаУезжаетВИсториюДляМодели() {
        SpyTool tool = new SpyTool(ToolResult.ok("java.version = 25"));
        ScriptedChatModel model = new ScriptedChatModel()
                .then(toolCall("call-1", "Spy", "{\"value\":\"pom.xml\"}"))
                .then(new AssistantMessage("Готово."));

        agent(model, publisher, tool).run("проверь");

        // Результат привязан к id вызова, а не к имени: за один ход модель может
        // попросить три чтения, и различить их она может только по id.
        ToolResponseMessage toolMessage = toolMessageOf(model.lastPrompt().getInstructions());
        assertThat(toolMessage.getResponses()).hasSize(1);
        assertThat(toolMessage.getResponses().getFirst().id()).isEqualTo("call-1");
        assertThat(toolMessage.getResponses().getFirst().responseData()).isEqualTo("java.version = 25");
    }

    @Test
    void ошибкаИнструментаВозвращаетсяМоделиАНеЛоматАгента() {
        SpyTool tool = new SpyTool(ToolResult.error("файл не найден: nope.txt"));
        ScriptedChatModel model = new ScriptedChatModel()
                .then(toolCall("call-1", "Spy", "{\"value\":\"nope.txt\"}"))
                .then(new AssistantMessage("Такого файла нет."));

        String answer = agent(model, publisher, tool).run("прочитай nope.txt");

        assertThat(answer).isEqualTo("Такого файла нет.");
        assertThat(toolMessageOf(model.lastPrompt().getInstructions()).getResponses().getFirst().responseData())
                .contains("файл не найден");
    }

    @Test
    void несколькоВызововЗаОдинХод() {
        SpyTool tool = new SpyTool(ToolResult.ok("ok"));
        // Независимые вызовы модель присылает пачкой в одном сообщении, а не по очереди.
        ScriptedChatModel model = new ScriptedChatModel()
                .then(AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(
                                new AssistantMessage.ToolCall("a", "function", "Spy", "{\"value\":\"one\"}"),
                                new AssistantMessage.ToolCall("b", "function", "Spy", "{\"value\":\"two\"}")))
                        .build())
                .then(new AssistantMessage("Оба прочитаны."));

        // Текст задания здесь декоративный: подставная модель его не читает, она отдаёт
        // сценарий. Предмет проверки — что цикл сделает с одним сообщением, где вызова два.
        agent(model, publisher, tool).run("прочитай оба");

        // Оба и в том же порядке. Цикл, который взял бы только getToolCalls().getFirst(),
        // прошёл бы все тесты выше и терял половину работы на живой модели.
        assertThat(tool.calls).containsExactly("one", "two");
    }

    @Test
    void неизвестныйИнструментНеРоняетАгента() {
        ScriptedChatModel model = new ScriptedChatModel()
                .then(toolCall("call-1", "НетТакого", "{}"))
                .then(new AssistantMessage("Извини."));

        String answer = agent(model, publisher).run("позови несуществующее");

        assertThat(answer).isEqualTo("Извини.");
        assertThat(toolMessageOf(model.lastPrompt().getInstructions()).getResponses().getFirst().responseData())
                .contains("нет инструмента");
    }
}
