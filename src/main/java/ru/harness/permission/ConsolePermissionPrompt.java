package ru.harness.permission;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Показываем вопрос в консоли и читаем ответ человека: y — один раз, a — разрешить и запомнить
 * предложенное, n или нет ввода — отказ. Что именно запомнится, решает проверка, задавшая вопрос;
 * диалог только показывает это и возвращает выбор PermissionGate.
 */
@Component
@Profile("!test")
public class ConsolePermissionPrompt implements PermissionPrompt {
    private final BufferedReader input;
    private final PrintStream output;

    public ConsolePermissionPrompt() {
        // Рабочий диалог читает UTF-8 из stdin; второй конструктор позволяет тестам подставить строки.
        this(new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)), System.out);
    }

    ConsolePermissionPrompt(BufferedReader input, PrintStream output) {
        this.input = input;
        this.output = output;
    }

    @Override
    public Answer ask(String toolName, String target, PermissionDecision decision) {
        String remember = describeAll(decision.suggestions());
        printQuestion(toolName, target, decision.reason(), remember);
        Answer answer = parse(readLine());
        return (answer == Answer.ALLOW_ALWAYS && remember == null) ? Answer.DENY : answer;
    }
    private static @Nullable String describeAll(List<PermissionUpdate> suggestions) {
        if (suggestions.isEmpty()) return null;
        return suggestions.stream().map(ConsolePermissionPrompt::describe).collect(Collectors.joining(" + "));
    }

    /** Строка для человека: что изменится и надолго ли. */
    static String describe(PermissionUpdate update) {
        String scope = update.destination() == PermissionSource.SESSION
                ? "на сессию"
                : "в настройках " + update.destination().name().toLowerCase(Locale.ROOT);
        return switch (update) {
            case PermissionUpdate.Directory added -> "рабочий каталог " + added.path() + " " + scope;
            case PermissionUpdate.Rule added -> added.behavior() + " " + added.rule() + " " + scope;
        };
    }

    private void printQuestion(String toolName, String target, String reason, @Nullable String remember) {
        output.printf("%n[ %s ] %s%n", toolName, target);
        output.println("  причина: " + reason);
        output.print("  разрешить? [y] один раз" + (remember == null ? "" : "  [a] и запомнить: " + remember) + "  [n] нет: ");
        // Принудительно выводим приглашение перед блокирующим чтением.
        output.flush();
    }

    /** null — ввода нет: stdin закрыт или чтение упало. Это не должно превратиться в разрешение. */
    private @Nullable String readLine() {
        try {
            String line = input.readLine();
            if (line == null) output.println("n (ввод недоступен)");
            return line;
        } catch (IOException unreadable) {
            output.println("n (ввод недоступен)");
            return null;
        }
    }

    /** Неизвестное слово, пустая строка, отсутствие ввода и n означают отказ. */
    private static Answer parse(@Nullable String line) {
        if (line == null) return Answer.DENY;
        return switch (line.strip().toLowerCase(Locale.ROOT)) {
            case "y", "yes", "д", "да" -> Answer.ALLOW_ONCE;
            case "a", "always", "в", "всегда" -> Answer.ALLOW_ALWAYS;
            default -> Answer.DENY;
        };
    }
}











