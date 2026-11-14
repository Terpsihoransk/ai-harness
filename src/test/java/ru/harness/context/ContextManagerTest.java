package ru.harness.context;

import ru.harness.agent.AgentProperties;
import ru.harness.tool.FileStateCache;
import ru.harness.tool.ToolRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Сборка контекста целиком: что попадает в промпт, чего в нём может не быть
 * и сколько раз он считается.
 */
class ContextManagerTest {

    @TempDir
    Path folder;

    /** Пустая папка: не репозиторий, {@code HARNESS.md} нет. */
    private ContextManager manager() {
        AgentProperties properties = new AgentProperties(20, folder.toString(), true);
        ToolRegistry registry = new ToolRegistry(List.of(), event -> {
        }, new FileStateCache());
        return new ContextManager(
                new GitStatusProvider(properties),
                new ProjectMemoryLoader(properties),
                new SystemPromptBuilder(registry, properties),
                properties);
    }

    @Test
    void окружениеЕстьВсегда() {
        String prompt = manager().systemPrompt();

        assertThat(prompt).contains("# Environment", "Working directory: " + folder);
    }

    /**
     * Ни репозитория, ни памяти проекта — и это не ошибка: секций просто нет,
     * а агент продолжает работать.
     */
    @Test
    void безРепозиторияИПамятиСекцийПростоНет() {
        String prompt = manager().systemPrompt();

        assertThat(prompt).doesNotContain("# Repository", "# Project notes");
    }

    @Test
    void памятьПроектаУезжаетВПромпт() throws IOException {
        Files.writeString(folder.resolve(ProjectMemoryLoader.MEMORY_FILE),
                "Собирать через mvnw, Maven в PATH нет.\n");

        String prompt = manager().systemPrompt();

        assertThat(prompt).contains("# Project notes (HARNESS.md)", "Maven в PATH нет");
    }

    /**
     * Промпт считается один раз за сессию. Проверяем не скорость, а <b>неизменность</b>:
     * он уезжает в модель при каждом запросе, и если бы менялся от хода к ходу,
     * поплыло бы поведение агента.
     */
    @Test
    void промптНеМеняетсяМеждуХодами() throws IOException {
        ContextManager manager = manager();
        String первый = manager.systemPrompt();

        Files.writeString(folder.resolve(ProjectMemoryLoader.MEMORY_FILE), "появилось по ходу\n");

        assertThat(manager.systemPrompt()).isEqualTo(первый);
        assertThat(manager.systemPrompt()).doesNotContain("появилось по ходу");
    }
}
