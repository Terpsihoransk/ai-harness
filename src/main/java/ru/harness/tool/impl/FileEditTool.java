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
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class FileEditTool implements Tool<FileEditTool.Input> {

    private final WorkspaceGuard workspaceGuard;

    public FileEditTool(WorkspaceGuard workspaceGuard) {
        this.workspaceGuard = workspaceGuard;
    }

    @Override
    public String name() {
        return "Edit";
    }

    @Override
    public String description() {
        return """
                Replaces an exact piece of text in a file.

                Usage:
                - You MUST use Read on the file first. This tool fails if you did not
                - oldString must match the file exactly, whitespace included
                - The edit FAILS if oldString is not unique in the file. Either provide
                  a larger string with more surrounding context to make it unique,
                  or set replaceAll to change every instance
                - oldString and newString must differ
                """;
    }

    @Override
    public Class<Input> inputType() {
        return Input.class;
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public String renderToolUseMessage(Input input) {
        return input.filePath() == null ? tag() : tag() + " " + input.filePath();
    }

    @Override
    public PermissionDecision checkPermissions(Input input, ToolContext context) {
        return PathInspector.checkWrite(workspaceGuard,input.filePath(),context.permissions());
    }

    @Override
    public ToolResult call(Input input, ToolContext context) {
        if (input.filePath() == null || input.filePath().isBlank()) {
            return ToolResult.error("не указан файл: заполни поле filePath");
        }
        if (input.oldString() == null || input.newString() == null) {
            return ToolResult.error("нужны оба текста: oldString — что заменить, newString — на что");
        }
        if (input.oldString().equals(input.newString())) {
            return ToolResult.error("oldString и newString совпадают: заменять нечем");
        }
        Path file;
        try {
            file = workspaceGuard.resolve(input.filePath());
        } catch (WorkspaceGuard.WorkspaceViolationException e) {
            return ToolResult.error(e.getMessage());
        }
        if (!Files.exists(file)) {
            return ToolResult.error("файл не найден: " + input.filePath());
        }
        if (Files.isDirectory(file)) {
            return ToolResult.error(input.filePath() + " — это папка, а не файл");
        }
        String refusal = context.readFileState().whyCannotWrite(file);
        if (refusal != null) {
            return ToolResult.error("%s: %s".formatted(input.filePath(), refusal));
        }
        String before;
        try {
            before = Files.readString(file);
        } catch (IOException e) {
            return ToolResult.error("не удалось прочитать %s: %s".formatted(input.filePath(), e.getMessage()));
        }
        int found = occurrences(before, input.oldString());
        if (found == 0) {
            return ToolResult.error(("текст не найден в %s. Прочитай файл инструментом Read"
                    + " и скопируй фрагмент точно").formatted(input.filePath()));
        }
        boolean replaceAll = Boolean.TRUE.equals(input.replaceAll());
        if (found > 1 && !replaceAll) {
            return ToolResult.error(("текст встречается %d раза в %s. Добавь соседних строк,"
                    + " чтобы фрагмент стал уникальным, или укажи replaceAll")
                    .formatted(found, input.filePath()));
        }
        String after = before.replace(input.oldString(), input.newString());
        try {
            Files.writeString(file, after);
        } catch (IOException e) {
            return ToolResult.error("не удалось записать %s: %s".formatted(input.filePath(), e.getMessage()));
        }
        context.readFileState().remember(file);

        return ToolResult.ok("заменено в %s, вхождений: %d".formatted(input.filePath(), replaceAll ? found : 1));
    }

    private static int occurrences(String haystack, String needle) {
        if (needle.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (int at = haystack.indexOf(needle); at >= 0; at = haystack.indexOf(needle, at + needle.length())) {
            count++;
        }
        return count;
    }

    // Так выглядит вызов от модели:
    //   {"filePath": "src/App.java", "oldString": "int x = 1;", "newString": "int x = 2;"}
    public record Input(
            @ToolParam(description = "Path to the file, relative to the project root")
            String filePath,

            @ToolParam(description = "Exact text to replace, whitespace included")
            String oldString,

            @ToolParam(description = "Text to put in its place")
            String newString,

            @Nullable @ToolParam(required = false, description = "Replace every occurrence instead of failing on duplicates")
            Boolean replaceAll) {
    }
}
