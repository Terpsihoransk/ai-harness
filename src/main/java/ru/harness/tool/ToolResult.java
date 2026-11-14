package ru.harness.tool;

import java.util.ArrayList;
import java.util.List;

public record ToolResult(String content, boolean error) {
    public static ToolResult ok(String content) {
        return new ToolResult(content, false);
    }

    public static ToolResult error(String content) {
        return new ToolResult(content, true);
    }

    public String preview(int maxLines, int maxChars) {
        List<String> lines = new ArrayList<>(content.lines()
                .limit(maxLines)
                .map(line -> line.length() <= maxChars ? line : line.substring(0, maxChars) + "…")
                .toList());
        if (content.lines().count() > maxLines) {
            lines.add("…");
        }
        return String.join("\n", lines);
    }

}
