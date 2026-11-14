package ru.harness.tool.impl;

import ru.harness.TestPermissionConfig;
import ru.harness.agent.AgentProperties;
import ru.harness.permission.PermissionBehavior;
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

class FileEditToolTest {

    @TempDir Path workspace;

    private final FileStateCache cache = new FileStateCache();
    private final ToolContext context = TestPermissionConfig.context(cache);

    private FileEditTool tool;

    @BeforeEach
    void setUp() {
        tool =
                new FileEditTool(
                        new WorkspaceGuard(new AgentProperties(20, workspace.toString(), true)));
    }

    private ToolResult edit(String path, String oldString, String newString, Boolean replaceAll) {
        return tool.call(new FileEditTool.Input(path, oldString, newString, replaceAll), context);
    }

    /** Файл, который агент «прочитал»: именно так его кладёт в кэш {@code Read}. */
    private Path прочитанный(String name, String content) throws IOException {
        Path file = Files.writeString(workspace.resolve(name), content);
        cache.remember(file);
        return file;
    }

    @Test
    void заменяетТочноеВхождение() throws IOException {
        Path file = прочитанный("Agent.java", "class Agent {\n    int limit = 1;\n}\n");

        ToolResult result = edit("Agent.java", "int limit = 1;", "int limit = 20;", null);

        assertThat(result.error()).isFalse();
        assertThat(Files.readString(file)).contains("int limit = 20;");
    }

    /**
     * Главный инвариант урока. Модель не видит диск: если файл не читали, она правит содержимое,
     * которое придумала.
     */
    @Test
    void непрочитанныйФайлПравитьНельзя() throws IOException {
        Files.writeString(workspace.resolve("Agent.java"), "class Agent {}\n");

        ToolResult result = edit("Agent.java", "class Agent {}", "class Agent { }", null);

        assertThat(result.error()).isTrue();
        assertThat(result.content()).contains("не был прочитан", "Read");
    }

    @Test
    void изменившийсяПослеЧтенияФайлПравитьНельзя() throws IOException {
        Path file = прочитанный("Agent.java", "class Agent {}\n");
        Files.writeString(file, "class Agent { void run() {} }\n");
        Files.setLastModifiedTime(
                file,
                java.nio.file.attribute.FileTime.fromMillis(
                        Files.getLastModifiedTime(file).toMillis() + 5_000));

        ToolResult result = edit("Agent.java", "class Agent {", "class Robot {", null);

        assertThat(result.error()).isTrue();
        assertThat(result.content()).contains("изменился");
    }

    /**
     * Два одинаковых фрагмента — мы не угадываем, какой из них имелся в виду. Текст ошибки называет
     * оба выхода: уточнить фрагмент или разрешить замену всех.
     */
    @Test
    void неуникальныйФрагментЭтоОшибкаСДвумяВыходами() throws IOException {
        прочитанный("Agent.java", "int x = 1;\nint x = 1;\n");

        ToolResult result = edit("Agent.java", "int x = 1;", "int x = 2;", null);

        assertThat(result.error()).isTrue();
        assertThat(result.content()).contains("встречается 2", "replaceAll");
    }

    @Test
    void сReplaceAllЗаменяетВсеВхождения() throws IOException {
        Path file = прочитанный("Agent.java", "int x = 1;\nint x = 1;\n");

        ToolResult result = edit("Agent.java", "int x = 1;", "int x = 2;", true);

        assertThat(result.error()).isFalse();
        assertThat(Files.readString(file)).isEqualTo("int x = 2;\nint x = 2;\n");
        assertThat(result.content()).contains("2");
    }

    /**
     * Промах правки ничего не стоит: файл цел, а модель получает текст, по которому видно, что
     * делать дальше.
     */
    @Test
    void ненайденныйТекстЭтоОшибкаАФайлЦел() throws IOException {
        Path file = прочитанный("Agent.java", "class Agent {}\n");

        ToolResult result = edit("Agent.java", "class Robot {}", "class Android {}", null);

        assertThat(result.error()).isTrue();
        assertThat(result.content()).contains("текст не найден", "Read");
        assertThat(Files.readString(file)).isEqualTo("class Agent {}\n");
    }

    @Test
    void послеПравкиМожноПравитьСнова() throws IOException {
        прочитанный("Agent.java", "int x = 1;\n");

        edit("Agent.java", "int x = 1;", "int x = 2;", null);
        ToolResult second = edit("Agent.java", "int x = 2;", "int x = 3;", null);

        assertThat(second.error()).isFalse();
    }

    @Test
    void одинаковыеТекстыЭтоОшибка() throws IOException {
        прочитанный("Agent.java", "int x = 1;\n");

        ToolResult result = edit("Agent.java", "int x = 1;", "int x = 1;", null);

        assertThat(result.error()).isTrue();
        assertThat(result.content()).contains("совпадают");
    }

    @Test
    void несуществующийФайлЭтоОшибка() {
        ToolResult result = edit("нет-такого.java", "a", "b", null);

        assertThat(result.error()).isTrue();
        assertThat(result.content()).contains("файл не найден");
    }

    @Test
    void путьСнаружиТребуетРазрешенияДоВыполнения() {
        var decision =
                tool.checkPermissions(
                        new FileEditTool.Input("../outside.txt", "a", "b", null), context);
        assertThat(decision.behavior()).isEqualTo(PermissionBehavior.ASK);
        assertThat(decision.reason()).contains("вне рабочих каталогов");
    }
}
