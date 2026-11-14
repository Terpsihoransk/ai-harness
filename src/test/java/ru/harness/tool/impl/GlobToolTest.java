package ru.harness.tool.impl;

import ru.harness.agent.AgentProperties;
import ru.harness.tool.ToolResult;
import ru.harness.tool.WorkspaceGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GlobToolTest {

    @TempDir
    Path workspace;

    private GlobTool tool;

    @BeforeEach
    void setUp() {
        tool = new GlobTool(new WorkspaceGuard(new AgentProperties(20, workspace.toString())));
    }

    private ToolResult glob(String pattern, String path) {
        return tool.call(new GlobTool.Input(pattern, path));
    }

    private void create(String relativePath) throws IOException {
        Path file = workspace.resolve(relativePath);
        Files.createDirectories(file.getParent() == null ? workspace : file.getParent());
        Files.writeString(file, "// пусто\n");
    }

    @Test
    void находитФайлыПоШаблону() throws IOException {
        create("src/main/java/Agent.java");
        create("src/main/java/Hud.java");
        create("README.md");

        ToolResult result = glob("**/*.java", null);

        assertThat(result.error()).isFalse();
        assertThat(result.content()).contains("src/main/java/Agent.java", "src/main/java/Hud.java");
        assertThat(result.content()).doesNotContain("README.md");
    }

    /**
     * {@code PathMatcher} из JDK требует для {@code **}{@code /} хотя бы один разделитель,
     * то есть файл в корне поиска таким шаблоном не находится. Модель об этом не знает
     * и пишет {@code **}{@code /*.java}, подразумевая «везде», — лечим на своей стороне.
     */
    @Test
    void шаблонСоЗвёздочкамиНаходитИФайлВКорне() throws IOException {
        create("Root.java");

        ToolResult result = glob("**/*.java", null);

        assertThat(result.content()).contains("Root.java");
    }

    @Test
    void свежиеФайлыИдутПервыми() throws IOException {
        create("old.java");
        create("new.java");
        Files.setLastModifiedTime(
                workspace.resolve("old.java"), java.nio.file.attribute.FileTime.fromMillis(1_000_000));

        ToolResult result = glob("*.java", null);

        assertThat(result.content().indexOf("new.java")).isLessThan(result.content().indexOf("old.java"));
    }

    @Test
    void поискМожноОграничитьПапкой() throws IOException {
        create("src/Inside.java");
        create("other/Outside.java");

        ToolResult result = glob("**/*.java", "src");

        assertThat(result.content()).contains("src/Inside.java");
        assertThat(result.content()).doesNotContain("Outside.java");
    }

    @Test
    void служебныеПапкиНеПоказываем() throws IOException {
        create("target/classes/Generated.java");
        create("src/Real.java");

        ToolResult result = glob("**/*.java", null);

        assertThat(result.content()).contains("src/Real.java");
        assertThat(result.content()).doesNotContain("Generated.java");
    }

    @Test
    void ничегоНеНайденоЭтоНеОшибка() {
        ToolResult result = glob("**/*.kt", null);

        assertThat(result.error()).isFalse();
        assertThat(result.content()).contains("ничего не найдено");
    }

    @Test
    void выходЗаРабочуюПапкуЗапрещён() {
        ToolResult result = glob("*.java", "../..");

        assertThat(result.error()).isTrue();
        assertThat(result.content()).contains("вне рабочей папки");
    }

    /**
     * Папки нет — это ошибка поиска, а не пустая выдача: ответ «ничего не найдено»
     * сказал бы агенту, что искать было где. Тот же ответ, что и у {@link GrepTool}:
     * язык шаблонов у них общий, значит и причины отказа должны совпадать.
     */
    @Test
    void shouldFailWhenSearchDirectoryDoesNotExist() {
        ToolResult result = glob("*.java", "src/nowhere");

        assertThat(result.error()).isTrue();
        assertThat(result.content()).contains("не удалось обойти папку");
    }
}
