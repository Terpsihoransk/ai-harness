package ru.harness.tool.impl;

import ru.harness.permission.PathInspector;
import ru.harness.permission.PermissionDecision;
import ru.harness.tool.Tool;
import ru.harness.tool.ToolContext;
import ru.harness.tool.ToolResult;
import ru.harness.tool.WorkspaceGuard;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class FileWriteTool implements Tool<FileWriteTool.Input> {

    private final WorkspaceGuard workspaceGuard;

    public FileWriteTool(WorkspaceGuard workspaceGuard) {
        this.workspaceGuard = workspaceGuard;
    }

    @Override
    public String name() {
        return "Write";
    }

    @Override
    public String description() {
        return """
                Writes a file to the local filesystem, overwriting it if it exists.
                
                Usage:
                - Use this tool to create a new file or to rewrite one completely
                - If the file exists, you MUST use Read on it first. This tool fails
                  if you did not
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
        if (input.content() == null) {
            return ToolResult.error("не указано содержимое: заполни поле content");
        }
        Path file;
        try {
            file = workspaceGuard.resolve(input.filePath());
        } catch (WorkspaceGuard.WorkspaceViolationException e) {
            return ToolResult.error(e.getMessage());
        }
        if (Files.isDirectory(file)) {
            return ToolResult.error(input.filePath() + " — это папка, а не файл");
        }
        String refusal = context.readFileState().whyCannotWrite(file);
        if (refusal != null) {
            return ToolResult.error("%s: %s".formatted(input.filePath(), refusal));
        }
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, input.content());
        } catch (IOException e) {
            return ToolResult.error("не удалось записать %s: %s".formatted(input.filePath(), e.getMessage()));
        }
        context.readFileState().remember(file);
        return ToolResult.ok("записан %s, строк: %d"
                .formatted(input.filePath(), input.content().lines().count()));

    }

    // Так выглядит вызов от модели:
    //   {"filePath": "src/App.java", "content": "class App {}\n"}
    public record Input(
            @ToolParam(description = "Path to the file, relative to the project root")
            String filePath,

            @ToolParam(description = "Full new content of the file")
            String content) {
    }
}
