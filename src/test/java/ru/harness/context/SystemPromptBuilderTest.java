package ru.harness.context;

import ru.harness.TestPermissionConfig;
import ru.harness.agent.AgentProperties;
import ru.harness.tool.FileStateCache;
import ru.harness.tool.Tool;
import ru.harness.tool.ToolContext;
import ru.harness.tool.ToolRegistry;
import ru.harness.tool.ToolResult;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SystemPromptBuilderTest {

    private static final ApplicationEventPublisher БЕЗ_СОБЫТИЙ = event -> {
    };

    /** Инструмент-пустышка: промпту нужны только имя и первая строка описания. */
    static class FakeTool implements Tool<String> {
        @Override
        public String name() {
            return "Read";
        }

        @Override
        public String description() {
            return "Reads a file from the local filesystem.\nUsage: ...";
        }

        @Override
        public Class<String> inputType() {
            return String.class;
        }

        @Override
        public ToolResult call(String input, ToolContext toolContext) {
            return ToolResult.ok("");
        }
    }

    private SystemPromptBuilder builder(boolean personality) {
        ToolRegistry registry =
                TestPermissionConfig.registry(
                        List.of(new FakeTool()),
                        БЕЗ_СОБЫТИЙ,
                        new FileStateCache(),
                        TestPermissionConfig.permissive(),
                        TestPermissionConfig.allowOnce());
        return new SystemPromptBuilder(
                registry, new AgentProperties(20, ".", personality));
    }

    @Test
    void сначалаКтоТыПотомКакСебяВести() {
        String prompt = builder(false).build(List.of());

        assertThat(prompt).contains("You are Harness", "# Doing tasks", "# Tone and style");
        assertThat(prompt.indexOf("You are Harness"))
                .as("представление идёт раньше правил работы")
                .isLessThan(prompt.indexOf("# Doing tasks"));
    }

    @Test
    void динамическийКонтекстИдётПослеСтатики() {
        String prompt = builder(false).build(List.of("# Environment\nWorking directory: /tmp"));

        assertThat(prompt.indexOf("# Tone and style")).isLessThan(prompt.indexOf("# Environment"));
    }

    /**
     * Инвентарь, а не перечисление через запятую: с числом и по строке на инструмент.
     * Плоский список модель читала невнимательно и теряла из него элементы.
     */
    @Test
    void перечисляетИнструментыСЯвнымЧислом() {
        String prompt = builder(false).build(List.of());

        assertThat(prompt).contains("You have exactly 1 tools");
        assertThat(prompt).contains("* Read — Reads a file from the local filesystem.");
        assertThat(prompt)
                .as("в промпт едет только первая строка описания, остальное — в схеме инструмента")
                .doesNotContain("Usage: ...");
    }

    /** Главный тезис урока: характер — это данные, а не код. */
    @Test
    void характерЭтоОднаСекцияКоторуюМожноВыключить() {
        assertThat(builder(true).build(List.of())).contains("T-800");
        assertThat(builder(false).build(List.of())).doesNotContain("T-800");
    }

    @Test
    void характерНеЗатрагиваетТехническиеСекции() {
        String сХарактером = builder(true).build(List.of());
        String без = builder(false).build(List.of());

        // Выключение характера убирает ровно одну секцию и ничего больше:
        // всё техническое содержимое остаётся тем же самым текстом.
        assertThat(сХарактером).startsWith(без);
        assertThat(сХарактером.substring(без.length())).contains("# Manner of speech");
    }
}
