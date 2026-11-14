package ru.harness.tool;

public record ToolResult (String content, boolean error){
    public static ToolResult ok(String content) {
        return new ToolResult(content, false);
    }
    public static ToolResult error(String content) {
        return new ToolResult(content, true);
    }

    public String preview(int maxChars) {
        String firstLine = content.lines().findFirst().orElse("");
        return firstLine.length() <= maxChars ? firstLine : firstLine.substring(0, maxChars) + "…";
    }
}
