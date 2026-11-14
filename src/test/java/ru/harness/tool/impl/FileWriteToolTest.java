package ru.harness.tool.impl;

import ru.harness.agent.AgentProperties;
import ru.harness.tool.FileStateCache;
import ru.harness.tool.ToolContext;
import ru.harness.tool.ToolResult;
import ru.harness.tool.WorkspaceGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FileWriteToolTest {

    @TempDir
    Path workspace;

    private final FileStateCache cache = new FileStateCache();
    private final ToolContext context = new ToolContext(cache);

    private FileWriteTool tool;

    @BeforeEach
    void setUp() {
        tool = new FileWriteTool(new WorkspaceGuard(new AgentProperties(20, workspace.toString(), true)));
    }

    private ToolResult write(String path, String content) {
        return tool.call(new FileWriteTool.Input(path, content), context);
    }

    @Test
    void создаётНовыйФайл() {
        ToolResult result = write("src/Agent.java", "class Agent {}\n");

        assertThat(result.error()).isFalse();
        assertThat(workspace.resolve("src/Agent.java")).exists();
    }

    @Test
    void создаётПапкиПоПути() {
        write("src/main/java/com/harness/Agent.java", "class Agent {}\n");

        assertThat(workspace.resolve("src/main/java/com/harness/Agent.java")).exists();
    }

    /**
     * Промах записи стоит всего содержимого файла — поэтому поверх существующего
     * пишем только то, что агент до этого прочитал.
     */
    @Test
    void поверхНепрочитанногоФайлаПисатьНельзя() throws IOException {
        Path file = Files.writeString(workspace.resolve("Agent.java"), "чужая работа\n");

        ToolResult result = write("Agent.java", "class Agent {}\n");

        assertThat(result.error()).isTrue();
        assertThat(result.content()).contains("не был прочитан", "Read");
        assertThat(Files.readString(file)).isEqualTo("чужая работа\n");
    }

    @Test
    void поверхПрочитанногоФайлаПисатьМожно() throws IOException {
        Path file = Files.writeString(workspace.resolve("Agent.java"), "class Agent {}\n");
        cache.remember(file);

        ToolResult result = write("Agent.java", "class Agent { void run() {} }\n");

        assertThat(result.error()).isFalse();
        assertThat(Files.readString(file)).isEqualTo("class Agent { void run() {} }\n");
    }

    @Test
    void послеЗаписиМожноПисатьСнова() {
        write("Agent.java", "раз\n");

        ToolResult second = write("Agent.java", "два\n");

        assertThat(second.error()).isFalse();
    }

    @Test
    void выходЗаРабочуюПапкуЗапрещён() {
        ToolResult result = write("../../чужое.java", "class Agent {}\n");

        assertThat(result.error()).isTrue();
        assertThat(result.content()).contains("вне рабочей папки");
    }

    @Test
    void записьПоверхПапкиЭтоОшибка() throws IOException {
        Files.createDirectory(workspace.resolve("src"));

        ToolResult result = write("src", "class Agent {}\n");

        assertThat(result.error()).isTrue();
        assertThat(result.content()).contains("это папка");
    }
}
