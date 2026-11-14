package ru.harness.tool.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.harness.agent.AgentProperties;
import ru.harness.tool.FileStateCache;
import ru.harness.tool.ToolContext;
import ru.harness.tool.ToolResult;
import ru.harness.tool.WorkspaceGuard;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class FileReadToolTest {

    @TempDir
    Path workspace;

    private final ToolContext context = new ToolContext(new FileStateCache());

    private FileReadTool tool;

    @BeforeEach
    void setUp() {
        tool = new FileReadTool(new WorkspaceGuard(new AgentProperties(20, workspace.toString(), true)));
    }

    private ToolResult read(String path, Integer offset, Integer limit) {
        return tool.call(new FileReadTool.Input(path, offset, limit), context);
    }

    private void write(String name, int lines) throws IOException {
        String content = IntStream.rangeClosed(1, lines)
                .mapToObj(i -> "строка" + i)
                .reduce((a, b) -> a + "\n" + b)
                .orElseThrow();
        Files.writeString(workspace.resolve(name), content);
    }

    @Test
    void читаетФайлВФорматеCatN() throws IOException {
        Files.writeString(workspace.resolve("hello.txt"), "первая\nвторая\n");

        ToolResult result = read("hello.txt", null, null);

        // Номера строк — не украшение: в уроке 5 ими будет целиться Edit.
        assertThat(result.error()).isFalse();
        assertThat(result.content()).isEqualTo("1\tпервая\n2\tвторая\n");
    }

    @Test
    void уважаетOffsetИLimit() throws IOException {
        write("many.txt", 10);

        ToolResult result = read("many.txt", 3, 2);

        assertThat(result.content()).contains("3\tстрока3").contains("4\tстрока4");
        assertThat(result.content()).doesNotContain("строка2").doesNotContain("строка5");
    }

    @Test
    void сообщаетЧтоФайлОбрезанЧтобыМодельМоглаДочитать() throws IOException {
        write("many.txt", 10);

        // Молча обрезать нельзя: модель решит, что видела файл целиком.
        assertThat(read("many.txt", 1, 3).content()).contains("offset");
    }

    @Test
    void отсутствующийФайлЭтоОшибкаАНеИсключение() {
        ToolResult result = read("nope.txt", null, null);

        assertThat(result.error()).isTrue();
        assertThat(result.content()).contains("файл не найден");
    }

    @Test
    void заПределыРабочейПапкиНеВыпускает() {
        ToolResult result = read("../../../etc/passwd", null, null);

        assertThat(result.error()).isTrue();
        assertThat(result.content()).contains("вне рабочей папки");
    }

    @Test
    void схемаГенерируетсяИзRecordА() {
        assertThat(tool.inputSchema()).contains("filePath").contains("offset").contains("limit");
    }
}
