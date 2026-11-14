package ru.harness.tool.impl;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import ru.harness.tool.Tool;
import ru.harness.tool.ToolResult;
import ru.harness.tool.WorkspaceGuard;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Component
public class GlobTool implements Tool<GlobTool.Input> {

    private final WorkspaceGuard workspaceGuard;

    public static final int MAX_RESULTS = 100;

    public GlobTool(WorkspaceGuard workspaceGuard) {
        this.workspaceGuard = workspaceGuard;
    }

    @Override
    public String name() {
        return "Glob";
    }

    @Override
    public String description() {
        return """
                Finds files by name pattern. Use this when you know what a file is called
                but not where it is.
                
                Usage:
                - Supports patterns like "**/*.java" or "**/Tool*.java"
                - Returns paths relative to the project root, most recently modified first
                - Returns at most %d results
                """.formatted(MAX_RESULTS);
    }

    @Override
    public Class<Input> inputType() {
        return Input.class;
    }

    @Override
    public ToolResult call(Input input) {
        if (input.pattern() == null || input.pattern().isBlank()) {
            return ToolResult.error("не указан шаблон поиска");
        }

        Path root;
        try {
            root = input.path() == null ? workspaceGuard.root() : workspaceGuard.resolve(input.path());
        } catch (WorkspaceGuard.WorkspaceViolationException e) {
            return ToolResult.error(e.getMessage());
        }
        PathMatcher matcher;
        try {
            matcher = matcherFor(input.pattern());
        } catch (IllegalArgumentException e) {
            return ToolResult.error("непонятный шаблон '%s': %s".formatted(input.pattern(), e.getMessage()));
        }

        List<Path> found;
        try (Stream<Path> walk = Files.walk(root)) {
            found = walk.filter(Files::isRegularFile)
                    .filter(path -> !Ignored.contains(root, path))
                    .filter(path -> matcher.matches(root.relativize(path)))
                    .sorted(Comparator.comparing(GlobTool::modifiedAt).reversed())
                    .limit(MAX_RESULTS + 1)
                    .toList();
        } catch (IOException e) {
            return ToolResult.error("не удалось обойти папку: " + e.getMessage());
        }
        if (found.isEmpty()) {
            return ToolResult.ok("ничего не найдено по шаблону " + input.pattern());
        }

        StringBuilder out = new StringBuilder();
        found.stream().limit(MAX_RESULTS).forEach(path -> out.append(workspaceGuard.relativize(path)).append('\n'));
        if (found.size() > MAX_RESULTS) {
            out.append("… показаны первые %d совпадений, уточни шаблон".formatted(MAX_RESULTS));
        }

        return ToolResult.ok(out.toString());
    }

    @Override
    public String renderToolUseMessage(Input input) {
        return input.pattern() == null ? tag() : tag() + " " + input.pattern();
    }

    private static long modifiedAt(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * {@code PathMatcher} из JDK требует для {@code **}{@code /} хотя бы один разделитель,
     * то есть файл в корне поиска таким шаблоном не находится. Модель же пишет его,
     * подразумевая «везде», — поэтому проверяем заодно и вариант без префикса.
     */
    static PathMatcher matcherFor(String pattern) {
        PathMatcher asWritten = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        if (!pattern.startsWith("**/")) {
            return asWritten;
        }

        // т.к. в руте мы не ищем по дефолту, звёздочки нужно убрать
        PathMatcher atRoot = FileSystems.getDefault().getPathMatcher("glob:" + pattern.substring(3));
        return path -> asWritten.matches(path) || atRoot.matches(path);
    }


    // Так выглядит вызов от модели:
    //   {"pattern": "**/ToolRegistry*"}
    //   {"pattern": "*.java", "path": "src/main/java"}
    public record Input(
            @ToolParam(description = "Glob pattern, for example **/*.java or **/Tool*.java")
            String pattern,

            @Nullable @ToolParam(required = false, description = "Directory to search in, defaults to project root")
            String path) {
    }

}
