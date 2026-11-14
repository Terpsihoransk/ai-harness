package ru.harness.tool.impl;

import ru.harness.permission.PathInspector;
import ru.harness.permission.PermissionDecision;
import ru.harness.tool.Tool;
import ru.harness.tool.ToolContext;
import ru.harness.tool.ToolResult;
import ru.harness.tool.WorkspaceGuard;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

@Component
public class GrepTool implements Tool<GrepTool.Input> {
    private static final int MAX_MATCHES = 100;

    private final WorkspaceGuard workspaceGuard;

    public GrepTool(WorkspaceGuard workspaceGuard) {
        this.workspaceGuard = workspaceGuard;
    }

    public enum OutputMode {
        FILES_WITH_MATCHES,
        CONTENT,
        COUNT
    }

    @Override
    public String name() {
        return "Grep";
    }

    @Override
    public String description() {
        return """
                Searches inside files with a regular expression. Use this when you know
                what the code says but not which file says it.

                Usage:
                - Output modes: FILES_WITH_MATCHES lists paths (default), CONTENT shows matching
                  lines with line numbers, COUNT shows how many matches per file
                - Narrow the search with glob, for example **/*.java
                - Prefer FILES_WITH_MATCHES first, then read the interesting file with Read
                - Returns at most %d matches
                """.formatted(MAX_MATCHES);
    }

    @Override
    public Class<Input> inputType() {
        return Input.class;
    }


    /**
     * Поиск читает каталог: PermissionEvaluator вызывает Tool, Tool выбирает файловую проверку чтения.
     * В ОРИГИНАЛЕ поиск ещё и прячет запрещённые файлы: шаблоны из запретов Read передаются
     * поиску как список исключений, и модель не видит ни имён, ни строк из таких файлов.
     * У нас проверяется только каталог, в котором ищем.
     */
    @Override
    public PermissionDecision checkPermissions(Input input, ToolContext context) {
        return PathInspector.checkRead(workspaceGuard, searchPath(input), context.permissions());
    }

    /** Если path не задан, ищем от корня проекта. */
    private static String searchPath(Input input) {
        return input.path() == null ? "." : input.path();
    }

    @Override
    public ToolResult call(Input input, ToolContext toolContext) {
        if (input.pattern() == null || input.pattern().isBlank()) {
            return ToolResult.error(
                    "не указан шаблон поиска: заполни поле pattern — например \"@Test\" или \"class .*Tool\"");
        }

        int flags = Boolean.TRUE == input.ignoreCase() ? Pattern.CASE_INSENSITIVE : 0;
        Pattern regex;
        try {
            regex = Pattern.compile(input.pattern(), flags);
        } catch (PatternSyntaxException e) {
            return ToolResult.error("некорректное регулярное выражение: " + e.getMessage());
        }
        Path root;
        try {
            root = input.path() == null ? workspaceGuard.root() : workspaceGuard.resolve(input.path());
        } catch (WorkspaceGuard.WorkspaceViolationException e) {
            return ToolResult.error(e.getMessage());
        }
        if (!Files.isDirectory(root)) {
            return ToolResult.error("папка не найдена: " + input.path());
        }
        PathMatcher glob = input.glob() == null ? null : GlobTool.matcherFor(input.glob());
        List<Path> files;
        try (Stream<Path> walk = Files.walk(root)) {
            files = walk.filter(Files::isRegularFile)
                    .filter(path -> !Ignored.contains(root, path))
                    .filter(path -> glob == null || glob.matches(root.relativize(path)))
                    .toList();
        }
        catch (IOException e) {
            return ToolResult.error("не удалось обойти папку: " + e.getMessage());
        }

        OutputMode mode = input.outputMode() == null ? OutputMode.FILES_WITH_MATCHES : input.outputMode();
        return switch (mode) {
            case FILES_WITH_MATCHES -> renderFiles(files, regex, input.pattern());
            case COUNT -> renderCounts(files, regex, input.pattern());
            case CONTENT -> renderContent(files, regex, input.pattern());
        };
    }

    private ToolResult renderContent(List<Path> files, Pattern regex, String pattern) {

        StringBuilder out = new StringBuilder();
        int shown = 0;
        for (Path file : files) {
            List<String> lines = lines(file);
            for (int i = 0; i < lines.size() && shown < MAX_MATCHES; i++) {
                if (regex.matcher(lines.get(i)).find()) {
                    out.append("%s:%d:%s%n".formatted(workspaceGuard.relativize(file), i + 1, lines.get(i).strip()));
                    shown++;
                }
                if(shown>=MAX_MATCHES){
                    out.append("… показаны первые %d совпадений, уточни шаблон".formatted(MAX_MATCHES));
                    break;
                }
            }
        }
        return shown == 0 ? nothingFound(pattern) : ToolResult.ok(out.toString());
    }

    private ToolResult renderCounts(List<Path> files, Pattern regex, String pattern) {
        List<String> rows = new ArrayList<>();
        long total = 0;
        for (Path file : files) {
            long count = lines(file).stream()
                    .filter(line -> regex.matcher(line).find())
                    .count();
            if(count>0){
                rows.add("%s: %d".formatted(workspaceGuard.relativize(file), count));
                total+=count;
            }
        }
        if(rows.isEmpty()){
            return nothingFound(pattern);
        }
        rows.add("всего совпадений: %d в %d файлах".formatted(total, rows.size()));
        return ToolResult.ok(String.join("\n", rows));


    }

    /** Бинарный файл — не повод падать: просто в нём нечего искать. */
    private static List<String> lines(Path file) {
        try {
            return Files.readAllLines(file);
        } catch (MalformedInputException notText) {
            return List.of();
        } catch (IOException cannotRead) {
            return List.of();
        }
    }

    private ToolResult renderFiles(List<Path> files, Pattern regex, String pattern) {
        List<String> hits = new ArrayList<>();
        for (Path file : files) {
            if(lines(file).stream().anyMatch(line -> regex.matcher(line).find())) {
                hits.add(workspaceGuard.relativize(file));
            }
            if(hits.size() >=MAX_MATCHES) {
                break;
            }
        }
        if (hits.isEmpty()) {
            return nothingFound(pattern);
        }
        return ToolResult.ok(String.join("\n", hits)
                + "\n\nЭто только пути, содержимое файлов не прочитано."
                + " Если вопрос о том, что делает код, вызови сейчас Read по пути выше.");
    }

    /** Пустая выдача — это ответ, а не сбой: искали и не нашли. */
    private static ToolResult nothingFound(String pattern) {
        return ToolResult.ok("совпадений не найдено: " + pattern);
    }



    @Override
    public String renderToolUseMessage(Input input) {
        return input.pattern() == null ? tag() : tag() + " " + input.pattern();
    }

    // Так выглядит вызов от модели:
    //   {"pattern": "implements Tool"}
    //   {"pattern": "@Test", "glob": "**/*.java", "outputMode": "COUNT"}
    public record Input(
            @ToolParam(description = "Regular expression to search for")
            String pattern,

            @Nullable @ToolParam(required = false, description = "Directory to search in, defaults to project root")
            String path,

            @Nullable @ToolParam(required = false, description = "Only search files matching this glob, e.g. **/*.java")
            String glob,

            @Nullable @ToolParam(required = false, description = "FILES_WITH_MATCHES lists paths (default),"
                    + " CONTENT shows matching lines, COUNT shows match counts")
            OutputMode outputMode,

            @Nullable @ToolParam(required = false, description = "Case insensitive search")
            Boolean ignoreCase) {
    }
}
