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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GrepToolTest {

    @TempDir
    Path workspace;

    private GrepTool tool;

    @BeforeEach
    void setUp() {
        tool = new GrepTool(new WorkspaceGuard(new AgentProperties(20, workspace.toString())));
    }

    private ToolResult grep(String pattern, GrepTool.OutputMode mode) {
        return tool.call(new GrepTool.Input(pattern, null, null, mode, null));
    }

    private void create(String relativePath, String content) throws IOException {
        Path file = workspace.resolve(relativePath);
        Files.createDirectories(file.getParent() == null ? workspace : file.getParent());
        Files.writeString(file, content);
    }

    @Test
    void поУмолчаниюОтдаётТолькоПути() throws IOException {
        create("src/Agent.java", "class Agent {\n    void run() {}\n}\n");
        create("src/Hud.java", "class Hud {}\n");

        ToolResult result = grep("class Agent", null);

        assertThat(result.error()).isFalse();
        assertThat(result.content()).contains("src/Agent.java");
        assertThat(result.content()).doesNotContain("void run");
    }

    /**
     * Путь без содержимого легко принять за готовый ответ: модель получает
     * «вот файл» и описывает его по названию, не читая. Поэтому результат сам
     * называет следующий шаг.
     */
    @Test
    void списокПутейПодсказываетПрочитатьФайл() throws IOException {
        create("src/Agent.java", "class Agent {}\n");

        ToolResult result = grep("class Agent", null);

        assertThat(result.content()).contains("Read");
    }

    @Test
    void режимCONTENTПоказываетСтрокиСНомерами() throws IOException {
        create("src/Agent.java", "class Agent {\n    void run() {}\n}\n");

        ToolResult result = grep("void run", GrepTool.OutputMode.CONTENT);

        assertThat(result.content()).contains("src/Agent.java:2:void run() {}");
    }

    @Test
    void режимCOUNTСчитаетСовпаденияСамИПодводитИтог() throws IOException {
        create("src/Agent.java", "@Test\n@Test\n");
        create("src/Hud.java", "@Test\n");

        ToolResult result = grep("@Test", GrepTool.OutputMode.COUNT);

        assertThat(result.content()).contains("src/Agent.java: 2", "src/Hud.java: 1");
        assertThat(result.content()).contains("всего совпадений: 3 в 2 файлах");
    }

    @Test
    void шаблономGlobМожноСузитьПоиск() throws IOException {
        create("src/Agent.java", "class Agent {}\n");
        create("notes/Agent.md", "class Agent {}\n");

        ToolResult result = tool.call(new GrepTool.Input("class Agent", null, "**/*.java", null, null));

        assertThat(result.content()).contains("src/Agent.java");
        assertThat(result.content()).doesNotContain("Agent.md");
    }

    /** Поправка на {@code **}{@code /} живёт в {@link GlobTool#matcherFor} и работает здесь тоже. */
    @Test
    void шаблонСоЗвёздочкамиНаходитИФайлВКорне() throws IOException {
        create("Root.java", "class Agent {}\n");

        ToolResult result = tool.call(new GrepTool.Input("class Agent", null, "**/*.java", null, null));

        assertThat(result.content()).contains("Root.java");
    }

    @Test
    void служебныеПапкиНеПоказываем() throws IOException {
        create("target/classes/Generated.java", "class Agent {}\n");
        create("src/Real.java", "class Agent {}\n");

        ToolResult result = grep("class Agent", null);

        assertThat(result.content()).contains("src/Real.java");
        assertThat(result.content()).doesNotContain("Generated.java");
    }

    /**
     * «Совпадений не найдено» — полноценный ответ на вопрос, а не сбой. Ошибка — это
     * когда поиск выполнить не удалось: битая регулярка, нет папки. Разница тонкая,
     * но от неё зависит, пойдёт агент дальше или начнёт выкручиваться.
     */
    @Test
    void ничегоНеНайденоЭтоНеОшибка() throws IOException {
        create("src/Agent.java", "class Agent {}\n");

        ToolResult result = grep("Скайнет", null);

        assertThat(result.error()).isFalse();
        assertThat(result.content()).contains("совпадений не найдено");
    }

    @Test
    void битаяРегуляркаЭтоОшибка() {
        ToolResult result = grep("class (Agent", null);

        assertThat(result.error()).isTrue();
        assertThat(result.content()).contains("некорректное регулярное выражение");
    }

    @Test
    void бинарныйФайлНеРонаетПоиск() throws IOException {
        Files.write(workspace.resolve("icon.bin"), new byte[] {(byte) 0xC3, (byte) 0x28, (byte) 0xA9});
        create("src/Agent.java", "class Agent {}\n");

        ToolResult result = grep("class Agent", null);

        assertThat(result.error()).isFalse();
        assertThat(result.content()).contains("src/Agent.java");
    }

    @Test
    void выходЗаРабочуюПапкуЗапрещён() {
        ToolResult result = tool.call(new GrepTool.Input("class Agent", "../..", null, null, null));

        assertThat(result.error()).isTrue();
        assertThat(result.content()).contains("вне рабочей папки");
    }

    /**
     * Папки нет — это ошибка поиска, а не пустая выдача: шаблон мог быть верным,
     * искать оказалось негде. Агенту важна именно причина: по «не найдено» он
     * отвечает человеку, по «папки нет» — пробует другой путь.
     */
    @Test
    void папкиНетЭтоОшибка() {
        ToolResult result = tool.call(new GrepTool.Input("class Agent", "src/nowhere", null, null, null));

        assertThat(result.error()).isTrue();
        assertThat(result.content()).contains("папка не найдена");
    }

    @Test
    void регистрМожноИгнорировать() throws IOException {
        create("src/Agent.java", "class Agent {}\n");

        ToolResult strict = tool.call(new GrepTool.Input("CLASS AGENT", null, null, null, null));
        ToolResult relaxed = tool.call(new GrepTool.Input("CLASS AGENT", null, null, null, true));

        assertThat(strict.content()).contains("совпадений не найдено");
        assertThat(relaxed.content()).contains("src/Agent.java");
    }

    /**
     * Сколько совпадений обещала последняя строка — столько и показано, и обещание
     * напечатано один раз. Предел срабатывает посреди файла, и поиск обязан
     * остановиться целиком: если прервать только обход строк, следующий файл
     * доберёт строки сверх предела, а хвостовая строка повторится по разу на файл.
     *
     * <p>Число предела тест не знает — он читает его из самого ответа. Так тест
     * не ломается от правки константы и проверяет именно обещание инструмента.
     */
//    @Test
//    void shouldStopEntireSearchWhenMatchLimitReached() throws IOException {
//        for (int index = 0; index < 4; index++) {
//            create("src/File%d.java".formatted(index), "// совпадение\n".repeat(60));
//        }
//
//        List<String> lines = grep("совпадение", GrepTool.OutputMode.CONTENT).content().lines().toList();
//        List<String> promise = lines.stream().filter(line -> line.contains("показаны первые")).toList();
//
//        assertThat(promise).hasSize(1);
//        int promised = Integer.parseInt(promise.getFirst().replaceAll("\\D+", " ").strip());
//        assertThat(lines).hasSize(promised + 1);
//    }
}
