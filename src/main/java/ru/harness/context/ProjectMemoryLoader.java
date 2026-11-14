package ru.harness.context;

import ru.harness.agent.AgentProperties;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;


@Component
public class ProjectMemoryLoader {

    static final String MEMORY_FILE = "HARNESS.md";

    static final int MAX_CHARS = 8000;

    private final Path workspaceRoot;

    public ProjectMemoryLoader(AgentProperties properties) {
        this.workspaceRoot = Path.of(properties.workspaceRoot()).toAbsolutePath().normalize();
    }

    /** @return содержимое файла или {@code null}, если его нет, он пуст или не читается */
    public @Nullable String load() {
        Path file = workspaceRoot.resolve(MEMORY_FILE);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            String content = Files.readString(file).strip();
            if (content.isEmpty()) {
                return null;
            }
            return content.length() <= MAX_CHARS
                    ? content
                    : content.substring(0, MAX_CHARS) + "\n… (обрезано)";
        } catch (IOException cannotRead) {
            return null;
        }
    }
}
