package ru.harness.context;

import ru.harness.agent.AgentProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Источники динамического контекста: репозиторий и память проекта. */
class ContextSourcesTest {

    @TempDir
    Path folder;

    private AgentProperties в(Path root) {
        return new AgentProperties(20, root.toString(), true);
    }

    /** Не ошибка и не исключение: агент просто работает без этого знания. */
    @Test
    void вНеРепозиторииGitСтатусаПростоНет() {
        assertThat(new GitStatusProvider(в(folder)).describe()).isNull();
    }

    @Test
    void вРепозиторииКурсаВиднаВеткаИКоммиты() {
        Path project = Path.of(".").toAbsolutePath().normalize();
        assumeTrue(Files.isDirectory(project.resolve(".git")), "тест имеет смысл только в репозитории");

        String status = new GitStatusProvider(в(project)).describe();

        assertThat(status).isNotNull();
        assertThat(status).contains("Текущая ветка:", "Последние коммиты:");
    }

    @Test
    void памятиНетЕслиФайлаНет() {
        assertThat(new ProjectMemoryLoader(в(folder)).load()).isNull();
    }

    @Test
    void читаетHarnessMd() throws IOException {
        Files.writeString(folder.resolve(ProjectMemoryLoader.MEMORY_FILE), "# Правила\nСобирать через mvnw.\n");

        assertThat(new ProjectMemoryLoader(в(folder)).load()).contains("Собирать через mvnw");
    }

    @Test
    void пустойФайлПамятиРавносиленЕгоОтсутствию() throws IOException {
        Files.writeString(folder.resolve(ProjectMemoryLoader.MEMORY_FILE), "   \n\n");

        assertThat(new ProjectMemoryLoader(в(folder)).load()).isNull();
    }

    @Test
    void слишкомБольшаяПамятьОбрезается() throws IOException {
        Files.writeString(folder.resolve(ProjectMemoryLoader.MEMORY_FILE),
                "x".repeat(ProjectMemoryLoader.MAX_CHARS + 500));

        String loaded = new ProjectMemoryLoader(в(folder)).load();

        assertThat(loaded).isNotNull();
        assertThat(loaded).contains("(обрезано)");
        assertThat(loaded.length()).isLessThan(ProjectMemoryLoader.MAX_CHARS + 100);
    }
}
