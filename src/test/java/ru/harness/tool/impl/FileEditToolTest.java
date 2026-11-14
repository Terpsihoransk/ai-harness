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

import static org.assertj.core.api.Assertions.assertThat;

class FileEditToolTest {

    @TempDir
    Path workspace;

    private final ToolContext context = new ToolContext(new FileStateCache());

    private FileEditTool tool;
    private FileReadTool readTool;

    @BeforeEach
    void setUp() {
        WorkspaceGuard guard = new WorkspaceGuard(new AgentProperties(20, workspace.toString()));
        tool = new FileEditTool(guard);
        readTool = new FileReadTool(guard);
    }

    private ToolResult edit(String path, String oldText, String newText) {
        return edit(path, oldText, newText, null);
    }

    private ToolResult edit(String path, String oldText, String newText, Boolean replaceAll) {
        return tool.call(new FileEditTool.Input(path, oldText, newText, replaceAll), context);
    }

    /** Как в бою: правке предшествует чтение тем же инструментом. */
    private void read(String path) {
        readTool.call(new FileReadTool.Input(path, null, null), context);
    }

    @Test
    void правитМногострочныйФрагментВФайлеСCrlf() throws IOException {
        Path file = workspace.resolve("Sample.java");
        Files.writeString(file, "class Sample {\r\n    int answer = 0;\r\n}\r\n");

        read("Sample.java");
        // Модель видит файл через Read, а тот отдаёт текст с LF — в oldString приходит LF.
        ToolResult result = edit("Sample.java",
                "class Sample {\n    int answer = 0;\n}",
                "class Sample {\n    int answer = 42;\n}");

        assertThat(result.error()).isFalse();
        // Родной стиль файла сохраняется: на диске по-прежнему CRLF.
        assertThat(Files.readString(file)).isEqualTo("class Sample {\r\n    int answer = 42;\r\n}\r\n");
    }

    @Test
    void правитФайлСLfНеПереводяЕгоВCrlf() throws IOException {
        Path file = workspace.resolve("Lf.java");
        Files.writeString(file, "int a = 1;\nint b = 2;\n");

        read("Lf.java");
        ToolResult result = edit("Lf.java", "int a = 1;", "int a = 10;");

        assertThat(result.error()).isFalse();
        assertThat(Files.readString(file)).isEqualTo("int a = 10;\nint b = 2;\n");
        assertThat(Files.readString(file)).doesNotContain("\r");
    }

    @Test
    void промахЕслиФрагментаНетВФайле() throws IOException {
        Files.writeString(workspace.resolve("t.txt"), "Привет, мир\n");
        read("t.txt");

        ToolResult result = edit("t.txt", "такого текста в файле нет", "замена");

        assertThat(result.error()).isTrue();
        assertThat(result.content()).contains("текст не найден");
    }

    @Test
    void неуникальныйФрагментТребуетКонтекстаИлиReplaceAll() throws IOException {
        Files.writeString(workspace.resolve("t.txt"), "x = 1;\nx = 1;\n");
        read("t.txt");

        ToolResult result = edit("t.txt", "x = 1;", "x = 2;");

        assertThat(result.error()).isTrue();
        assertThat(result.content()).contains("встречается");
    }

    @Test
    void replaceAllЗаменяетВсеВхождения() throws IOException {
        Path file = workspace.resolve("t.txt");
        Files.writeString(file, "x = 1;\nx = 1;\n");
        read("t.txt");

        ToolResult result = edit("t.txt", "x = 1;", "x = 2;", true);

        assertThat(result.error()).isFalse();
        assertThat(Files.readString(file)).isEqualTo("x = 2;\nx = 2;\n");
    }

    @Test
    void писатьМожноТолькоПослеЧтения() throws IOException {
        Files.writeString(workspace.resolve("t.txt"), "строка\n");

        ToolResult result = edit("t.txt", "строка", "другая строка");

        assertThat(result.error()).isTrue();
        assertThat(result.content()).contains("не был прочитан");
    }

    @Test
    void одинаковыйТекстЗаменятьНечем() {
        ToolResult result = edit("t.txt", "одно и то же", "одно и то же");

        assertThat(result.error()).isTrue();
        assertThat(result.content()).contains("совпадают");
    }

    @Test
    void заПределыРабочейПапкиНеВыпускает() {
        ToolResult result = edit("../../../etc/passwd", "a", "b");

        assertThat(result.error()).isTrue();
        assertThat(result.content()).contains("вне рабочей папки");
    }

    @Test
    void схемаГенерируетсяИзRecordА() {
        assertThat(tool.inputSchema())
                .contains("filePath")
                .contains("oldString")
                .contains("newString");
    }
}
