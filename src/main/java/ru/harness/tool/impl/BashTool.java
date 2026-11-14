package ru.harness.tool.impl;

import ru.harness.permission.CommandInspector;
import ru.harness.permission.PermissionDecision;
import ru.harness.tool.Tool;
import ru.harness.tool.ToolContext;
import ru.harness.tool.ToolResult;
import ru.harness.tool.WorkspaceGuard;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Выполнение команды с ограничением времени и размера результата.
 */
@Component
public class BashTool implements Tool<BashTool.Input> {
    static final int MAX_OUTPUT_CHARS = 8_000;
    static final int DEFAULT_TIMEOUT_SECONDS = 120;

    private final WorkspaceGuard workspaceGuard;

    public BashTool(WorkspaceGuard workspaceGuard) {
        this.workspaceGuard = workspaceGuard;
    }

    public record Input(
            @ToolParam(description = "Shell command to execute")
            String command,

            @Nullable @ToolParam(required = false, description = "What this command does")
            String description,

            @Nullable
            @ToolParam(
                    required = false,
                    description = "Timeout in seconds, default 120, maximum 600")
            Integer timeoutSeconds) {
    }

    @Override
    public String name() {
        return "Bash";
    }

    @Override
    public String description() {
        return """
                Executes a shell command in the project directory and returns its exit code and output.
                - Use this to build and run tests. On Windows commands run in cmd.exe, otherwise /bin/sh.
                - Prefer Read, Grep, Glob and Edit for inspecting or editing files.
                - Only the last %d output characters are retained.
                - A command may require the user's confirmation or be refused."""
                .formatted(MAX_OUTPUT_CHARS);
    }

    @Override
    public Class<Input> inputType() {
        return Input.class;
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    /**
     * Показываем человеку саму команду.
     */
    @Override
    public String renderToolUseMessage(Input input) {
        return input.command() == null ? "" : input.command();
    }

    /** Сервис вызвал Tool. Tool передаёт проверку команды в CommandInspector. */
    @Override
    public PermissionDecision checkPermissions(Input input, ToolContext context) {
        return CommandInspector.check(input.command(), context.permissions());
    }

    @Override
    public ToolResult call(Input input, ToolContext context) {
        if (input.command() == null || input.command().isBlank()) return ToolResult.error("не указана команда");
        int timeout = input.timeoutSeconds() == null ? DEFAULT_TIMEOUT_SECONDS : input.timeoutSeconds();
        if (timeout < 1 || timeout > 600) return ToolResult.error("таймаут должен быть от 1 до 600 секунд");
        Path script = null;
        Process process = null;
        try {
            script = writeScript(input.command());
            process = startProcess(script);
            CompletableFuture<String> output = readOutputInBackground(process);
            if (!process.waitFor(timeout, TimeUnit.SECONDS)) return ToolResult.error("команда не уложилась в %d секунд и была прервана".formatted(timeout));
            return report(process.exitValue(), output.get(5, TimeUnit.SECONDS));
        } catch (IOException failure) {
            return ToolResult.error("не удалось запустить команду: " + failure.getMessage());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return ToolResult.error("выполнение прервано");
        } catch (ExecutionException | TimeoutException failure) {
            return ToolResult.error("не удалось прочитать вывод: " + failure.getMessage());
        } finally {
            cleanUp(process, script);
        }
    }

    private static ToolResult report(int exitCode, String output) {
        return ToolResult.ok("код возврата: %d%n%s".formatted(exitCode, output.isBlank() ? "(вывод пуст)" : output));
    }

    /**
     * Что бы ни случилось: процесс убит вместе с потомками, временный файл удалён.
     */
    private static void cleanUp(@Nullable Process process, @Nullable Path script) {
        if (process != null && process.isAlive()) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
        try {
            if (script != null) Files.deleteIfExists(script);
        } catch (IOException ignored) {
            // Ошибка уборки не подменяет результат команды.
        }
    }

    /**
     * Читаем вывод в отдельном потоке, пока основной ждёт завершения процесса. Без этого возможно
     * зависание: процесс заполнил буфер вывода и ждёт читателя, а мы ждём, когда он закончится.
     */
    private static CompletableFuture<String> readOutputInBackground(Process process) {
        return CompletableFuture.supplyAsync(() -> readTail(process.getInputStream()));
    }

    /**
     * Оставляем последние MAX_OUTPUT_CHARS символов: ошибка сборки и итог тестов печатаются в конце.
     */
    private static String readTail(InputStream stream) {
        StringBuilder tail = new StringBuilder();
        boolean truncated = false;
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            char[] buffer = new char[2048];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                tail.append(buffer, 0, read);
                if (tail.length() > MAX_OUTPUT_CHARS) {
                    tail.delete(0, tail.length() - MAX_OUTPUT_CHARS);
                    truncated = true;
                }
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        return (truncated ? "… (начало вывода обрезано)\n" : "") + tail;
    }



    /**
     * Команду пишем во временный файл: переданная аргументом, она доехала бы с испорченными кавычками.
     */
    private static Path writeScript(String command) throws IOException {
        Path script = Files.createTempFile("harness-", isWindows() ? ".cmd" : ".sh");
        // chcp 65001 переключает консоль Windows на UTF-8, иначе русский вывод приедет модели нечитаемым.
        String body = isWindows()
                ? "@echo off\r\nchcp 65001 > nul\r\n" + command + "\r\n"
                : "#!/bin/sh\n" + command + "\n";
        Files.writeString(script, body, StandardCharsets.UTF_8);
        return script;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    }

    /**
     * Запускаем оболочку в корне проекта; stderr идёт в тот же поток, что и stdout.
     */
    private Process startProcess(Path script) throws IOException {
        List<String> shell = isWindows() ? List.of("cmd.exe", "/d", "/c", script.toString()) : List.of("/bin/sh", script.toString());
        ProcessBuilder builder = new ProcessBuilder(shell).directory(workspaceGuard.root().toFile()).redirectErrorStream(true);
        provideJdk(builder.environment());
        Process process = builder.start();
        // Закрываем ввод: команда, которая решит что-то спросить, получит конец файла и завершится.
        process.getOutputStream().close();
        return process;
    }

    private static void provideJdk(Map<String, String> environment) {
        String javaHome = System.getProperty("java.home");
        if (javaHome == null || javaHome.isBlank()) return;
        environment.putIfAbsent("JAVA_HOME", javaHome);
        // В Windows переменная может называться Path, а не PATH.
        String pathKey = environment.keySet().stream().filter(key -> key.equalsIgnoreCase("PATH")).findFirst().orElse("PATH");
        String javaBin = Path.of(javaHome, "bin").toString();
        String path = environment.getOrDefault(pathKey, "");
        if (!path.contains(javaBin)) environment.put(pathKey, javaBin + File.pathSeparator + path);
    }

}