package ru.harness.permission;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConsolePermissionPromptTest {
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();

    private PermissionPrompt.Answer answer(String text) {
        return prompt(new StringReader(text)).ask("Bash", ".\\mvnw.cmd test",
                PermissionDecision.ask("выполнение команды")
                        .withSuggestions(List.of(PermissionUpdate.rule(
                                PermissionRule.parse("Bash(mvnw test)"),
                                PermissionBehavior.ALLOW,
                                PermissionSource.LOCAL))));
    }

    private ConsolePermissionPrompt prompt(Reader input) {
        return new ConsolePermissionPrompt(
                new BufferedReader(input), new PrintStream(output, true, StandardCharsets.UTF_8));
    }

    @Test
    void разрешениеОдинРазПоддерживаетРегистрИПробелы() {
        assertThat(answer(" Y \n")).isEqualTo(PermissionPrompt.Answer.ALLOW_ONCE);
    }

    @Test
    void разрешениеНаСессиюЭтоОтдельныйОтвет() {
        assertThat(answer("всегда\n")).isEqualTo(PermissionPrompt.Answer.ALLOW_ALWAYS);
    }

    @Test
    void неизвестныйОтветПустаяСтрокаИКонецВводаЭтоОтказ() {
        for (String text : List.of("n\n", "maybe\n", "\n", "")) {
            assertThat(answer(text)).isEqualTo(PermissionPrompt.Answer.DENY);
        }
    }

    @Test
    void ошибкаЧтенияНеСчитаетсяСогласием() {
        Reader broken =
                new Reader() {
                    @Override
                    public int read(char[] buffer, int offset, int length) throws IOException {
                        throw new IOException("нет ввода");
                    }

                    @Override
                    public void close() {}
                };
        assertThat(prompt(broken).ask("Bash", "command", PermissionDecision.ask("reason")))
                .isEqualTo(PermissionPrompt.Answer.DENY);
    }

    @Test
    void человекВидитЦельИПричину() {
        answer("y\n");
        assertThat(output.toString(StandardCharsets.UTF_8))
                .contains("Bash", ".\\mvnw.cmd test", "выполнение команды", "в настройках local");
    }

    private PermissionPrompt.Answer respond(String text, List<PermissionUpdate> suggestions) {
        return prompt(new StringReader(text)).ask(
                "Tool", "target", PermissionDecision.ask("причина").withSuggestions(suggestions));
    }

    @Test
    void запомнитьПоказываетВсеПредложенияИВозвращаетВыбор() {
        var suggestions = List.of(
                PermissionUpdate.rule(
                        PermissionRule.parse("Edit(/src/**)"),
                        PermissionBehavior.ALLOW,
                        PermissionSource.SESSION),
                PermissionUpdate.directory("/shared"));
        var response = respond("a\n", suggestions);
        assertThat(response).isEqualTo(PermissionPrompt.Answer.ALLOW_ALWAYS);
        assertThat(output.toString(StandardCharsets.UTF_8)).contains("[a]", "ALLOW Edit(/src/**) на сессию", "рабочий каталог /shared на сессию");
    }

    @Test
    void безПредложенийЗапомнитьНечего() {
        // Вариант a не показываем; введённое вручную a — отказ, а не тихое «один раз».
        var response = respond("a\n", List.of());
        assertThat(response).isEqualTo(PermissionPrompt.Answer.DENY);
        assertThat(output.toString(StandardCharsets.UTF_8)).doesNotContain("[a]");
    }

    @Test
    void одинРазНичегоНеЗапоминает() {
        var response = respond("y\n", List.of(PermissionUpdate.rule(PermissionRule.parse("Bash(./mvnw test)"), PermissionBehavior.ALLOW, PermissionSource.LOCAL)));
        assertThat(response).isEqualTo(PermissionPrompt.Answer.ALLOW_ONCE);
        assertThat(output.toString(StandardCharsets.UTF_8)).contains("ALLOW Bash(./mvnw test) в настройках local");
    }
}
