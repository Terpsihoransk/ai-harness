package ru.harness.context;

import ru.harness.agent.AgentProperties;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;


@Component
public class GitStatusProvider {

    /** Длинный статус вытеснит из окна саму задачу — поэтому предел. */
    static final int MAX_STATUS_CHARS = 2000;

    private static final int TIMEOUT_SECONDS = 5;

    private final Path workspaceRoot;

    public GitStatusProvider(AgentProperties properties) {
        this.workspaceRoot = Path.of(properties.workspaceRoot()).toAbsolutePath().normalize();
    }

    /**
     * @return блок для промпта или {@code null}, если это не репозиторий
     *         либо git недоступен — тогда просто молчим
     */
    public @Nullable String describe() {
        if (!Files.isDirectory(workspaceRoot.resolve(".git"))) {
            return null;
        }

        String branch = git("rev-parse", "--abbrev-ref", "HEAD");
        if (branch == null) {
            return null;
        }

        String status = git("--no-optional-locks", "status", "--short");
        String log = git("log", "--oneline", "-n", "5");

        StringBuilder out = new StringBuilder();
        out.append("Это состояние репозитория на начало разговора. Оно не обновляется по ходу.\n");
        out.append("Текущая ветка: ").append(branch).append('\n');
        out.append("\nИзменённые файлы:\n").append(truncate(status));
        if (log != null && !log.isBlank()) {
            out.append("\n\nПоследние коммиты:\n").append(log);
        }
        return out.toString();
    }

    private static String truncate(@Nullable String status) {
        if (status == null || status.isBlank()) {
            return "(нет изменений)";
        }
        if (status.length() <= MAX_STATUS_CHARS) {
            return status;
        }
        return status.substring(0, MAX_STATUS_CHARS)
                + "\n… (обрезано) Полный список смотри командой git status";
    }

    /** Ошибка запуска — не повод падать: без git-контекста агент просто работает как раньше. */
    private @Nullable String git(String... args) {
        List<String> command = new ArrayList<>(List.of("git"));
        command.addAll(List.of(args));
        try {
            Process process = new ProcessBuilder(command)
                    .directory(workspaceRoot.toFile())
                    .redirectErrorStream(false)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            return process.exitValue() == 0 ? output.strip() : null;
        } catch (IOException gitNotAvailable) {
            return null;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
