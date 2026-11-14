package ru.harness.hud;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.stereotype.Component;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.joining;
import static ru.harness.hud.LanguageUtils.plural;

/**
 * Экран красного интерфейса: точки-заполнители, отступы, цвета, переносы — и печать.
 *
 * <p><b>Этот класс можно не читать.</b> Здесь нет ни одного решения о том, что
 * показывать, — только о том, как это выглядит. {@link Hud} осматривает машину
 * и решает; экран рисует и печатает.
 *
 * <p>Правило проверяется глазами, а не на слово: здесь нет ни одного импорта
 * из {@code ru.harness.agent}, а в {@link Hud} нет ни {@link Ansi}, ни
 * {@code PrintStream}. За вторым следит тест, чтобы граница не размылась к
 * десятому уроку.
 *
 * <p>Поток вывода принадлежит экрану: раз печатает он, то и {@code System.out}
 * заводит он же. В тестах на его место приходит буфер.
 */
@Component
public class MachineViewPrinter {

    private static final String NEW_LINE = System.lineSeparator();

    private static final String BLANK = "";

    private static final String HEADER =
            Ansi.bold(Ansi.brightRed("CYBERDYNE SYSTEMS   SERIES T-800   BOOT SEQUENCE"));

    private static final String MISSION = Ansi.dim("MISSION: ждёт постановки задачи");

    private static final String CONTEXT_FOOTER = Ansi.dim("  └─ ПЕРЕДАНО В НЕЙРОПРОЦЕССОР");

    /** Колонка, до которой тянутся точки в boot-логе. */
    private static final int LABEL_WIDTH = 26;

    /** Столько строк показываем от одной реплики, дальше — «… ещё N строк». */
    private static final int MAX_ENTRY_LINES = 3;

    /** Ширина текстовой колонки в панели контекста. */
    private static final int CONTEXT_WIDTH = 64;

    /** Колонка, до которой выравнивается «[1] СОЗДАТЕЛЬ ·····». */
    private static final int ROLE_WIDTH = 18;

    private final PrintStream out;

    MachineViewPrinter() {
        // Явный UTF-8: иначе кириллица зависит от кодовой страницы консоли.
        this(new PrintStream(System.out, true, StandardCharsets.UTF_8));
    }

    /** Для тестов: вывод в буфер вместо консоли. */
    MachineViewPrinter(PrintStream out) {
        this.out = out;
    }

    /** Boot-последовательность целиком: шапка, строки подсистем, статус миссии. */
    void showBootLog(List<BootLine> lines) {
        String subsystems = lines.stream().map(line -> toString(line)).collect(joining(NEW_LINE));

        print(BLANK, HEADER, subsystems, MISSION, BLANK);
    }

    void showOrder(String userInput) {
        print(Ansi.red("> ПОЛУЧЕН ПРИКАЗ: ") + userInput);
    }

    /**
     * Как назвать себя, решает сам инструмент: он присылает готовую строку вида
     * {@code [READ] Tool.java}. Экран её не разбирает, только обрамляет —
     * «ЦЕЛЬ ОБНАРУЖЕНА» это уже наша метафора.
     */
    void showToolCallStarted(String target) {
        print(Ansi.red("> ЦЕЛЬ ОБНАРУЖЕНА: ") + target + Ansi.red(" — ЗАХВАТ"));
    }

    /**
     * Ошибка инструмента печатается в полный голос. Для модели она обычный результат,
     * но создателю за пультом надо видеть, что машина промахнулась.
     *
     * <p>Удачный вызов показывает ещё и начало результата: решение агент принимает
     * не по времени работы, а по тому, что инструмент вернул. Сколько строк
     * показывать, решено не здесь — экран печатает то, что пришло в событии.
     */
    void showToolCallFinished(boolean error, String preview, Duration duration) {
        if (error) {
            print(Ansi.bold(Ansi.red("> ПРОМАХ: ")) + preview);
            return;
        }
        String head = "> АНАЛИЗ ЗАВЕРШЁН · " + duration.toMillis() + " мс";
        if (preview.isBlank()) {
            print(Ansi.dim(head));
            return;
        }
        List<String> block = new ArrayList<>();
        block.add(Ansi.dim(head + " · В НЕЙРОПРОЦЕССОР:"));
        preview.lines().forEach(line -> block.add(rail(line)));
        print(block);
    }

    void showTurnLimitReached(int maxTurns) {
        print(Ansi.bold(Ansi.red("> ПРЕДЕЛ ИТЕРАЦИЙ (" + maxTurns + ") — МИССИЯ ПРЕРВАНА")));
    }

    /**
     * Итог хода. Число итераций — единственное место, где виден сам цикл:
     * в boot-логе его не показать, подсистемы там статичны.
     */
    void showTurnFinished(Duration duration, int iterations) {
        print(Ansi.dim("> ЦИКЛ ЗАВЕРШЁН · %s · %d мс"
                .formatted(plural(iterations, "итерация", "итерации", "итераций"), duration.toMillis())));
    }

    /**
     * Панель контекста: системный промпт и вся история в хронологическом порядке.
     *
     * <p>На вход — снимок, который {@link Hud} взял у разговора, и его цена
     * в токенах. Экран не знает, откуда снимок взялся, и сходить за ним сам
     * не может: {@code Conversation} для него не существует. Считать токены —
     * тоже не его дело: он показывает измеренное.
     */
    void showContext(List<Message> prompt, int tokens) {
        List<String> panel = new ArrayList<>();

        panel.add(contextHead(prompt, tokens));
        panel.add(section("СИСТЕМНЫЙ ПРОМПТ"));
        panel.addAll(systemPromptLines(prompt));
        panel.add(section("ИСТОРИЯ СООБЩЕНИЙ") + Ansi.dim(" · в хронологическом порядке"));
        panel.addAll(historyLines(prompt));
        panel.add(CONTEXT_FOOTER);

        print(panel);
    }

    /** Единственное место, которое трогает поток вывода. Блок уходит целиком, а не построчно. */
    private void print(String... blocks) {
        out.println(String.join(NEW_LINE, blocks));
    }

    private void print(List<String> blocks) {
        print(blocks.toArray(String[]::new));
    }

    private String contextHead(List<Message> prompt, int tokens) {
        return Ansi.dim("  ┌─ КОНТЕКСТ ХОДА · сообщений: %d · токенов: ~%d".formatted(prompt.size(), tokens));
    }

    private String section(String title) {
        return rail(Ansi.red(title));
    }

    private List<String> systemPromptLines(List<Message> prompt) {
        return prompt.stream()
                .filter(message -> message.getMessageType() == MessageType.SYSTEM)
                .flatMap(message -> wrap(text(message)).stream())
                .map(line -> rail("  " + line))
                .toList();
    }

    private List<String> historyLines(List<Message> prompt) {
        List<Message> history = prompt.stream()
                .filter(message -> message.getMessageType() != MessageType.SYSTEM)
                .toList();

        if (history.isEmpty()) {
            return List.of(rail(Ansi.dim("  (пусто — это первый ход)")));
        }

        List<String> lines = new ArrayList<>();
        for (int i = 0; i < history.size(); i++) {
            lines.addAll(historyEntry(i + 1, history.get(i)));
        }
        return lines;
    }

    private String toString(BootLine line) {
        String label = line.part().displayName() + " ";
        String dots = ".".repeat(Math.max(1, LABEL_WIDTH - label.length()));
        String status = Ansi.brightRed(line.status().name());
        String detail = String.join(", ", line.components());

        return "%s %s %s   %s".formatted(Ansi.red("> " + label), Ansi.dim(dots), status, Ansi.dim("(" + detail + ")"));
    }

    /** Одна реплика: «[1] СОЗДАТЕЛЬ ····· текст», продолжение — под текстом, а не под номером. */
    private List<String> historyEntry(int number, Message message) {
        String head = "[%d] %s ".formatted(number, role(message.getMessageType()));
        String prefix = head + ".".repeat(Math.max(1, ROLE_WIDTH - head.length())) + " ";
        String indent = " ".repeat(prefix.length());

        List<String> lines = new ArrayList<>();
        List<String> wrapped = clamp(wrap(text(message)));
        for (int i = 0; i < wrapped.size(); i++) {
            lines.add(rail(i == 0 ? Ansi.dim(prefix) + wrapped.get(i) : indent + wrapped.get(i)));
        }
        return lines;
    }

    /** Левая направляющая панели. Правого края нет намеренно: он бы разъезжался на любой ширине окна. */
    private static String rail(String content) {
        return Ansi.dim("  │ ") + content;
    }

    /**
     * Как назвать роль на экране — решает экран. Ядро знает только
     * {@code MessageType}, метафора живёт здесь.
     */
    private static String role(MessageType type) {
        return switch (type) {
            case SYSTEM -> "СИСТЕМА";
            case USER -> "СОЗДАТЕЛЬ";
            case ASSISTANT -> "T-800";
            case TOOL -> "ИНСТРУМЕНТ";
        };
    }

    /**
     * Текст реплики для панели. У вызова инструмента и у его ответа содержимое лежит
     * не в {@code getText()}, поэтому раньше такие реплики молча пропадали, а номера
     * шли «1, 6, 7». В контекст они уехали — значит, и на экране должны быть.
     */
    private static String text(Message message) {
        if (message instanceof AssistantMessage assistant && assistant.hasToolCalls()) {
            return assistant.getToolCalls().stream()
                    .map(call -> call.name() + " " + call.arguments())
                    .collect(joining("; "));
        }
        if (message instanceof ToolResponseMessage toolResponse) {
            return toolResponse.getResponses().stream()
                    .map(response -> response.responseData())
                    .collect(joining("; "));
        }
        return message.getText() == null ? "" : message.getText();
    }

    /** Длинную реплику сворачиваем: панель показывает состав контекста, а не его целиком. */
    private static List<String> clamp(List<String> lines) {
        if (lines.size() <= MAX_ENTRY_LINES) {
            return lines;
        }
        List<String> clamped = new ArrayList<>(lines.subList(0, MAX_ENTRY_LINES));
        clamped.add(Ansi.dim("… ещё %s".formatted(
                plural(lines.size() - MAX_ENTRY_LINES, "строка", "строки", "строк"))));
        return clamped;
    }

    /** Перенос по словам: слово целее строки. Длиннее ширины — режем, иначе панель разъедется. */
    private static List<String> wrap(String text) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.lines().toList()) {
            String rest = paragraph.strip();
            if (rest.isEmpty()) {
                continue;
            }
            while (rest.length() > CONTEXT_WIDTH) {
                int cut = rest.lastIndexOf(' ', CONTEXT_WIDTH);
                if (cut <= 0) {
                    cut = CONTEXT_WIDTH;
                }
                lines.add(rest.substring(0, cut).strip());
                rest = rest.substring(cut).strip();
            }
            lines.add(rest);
        }
        return lines;
    }

}
