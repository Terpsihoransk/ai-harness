package ru.harness.tool;

import org.springframework.stereotype.Component;
import ru.harness.agent.AgentProperties;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

@Component
public class WorkspaceGuard {
    private final Path root;

    public WorkspaceGuard(AgentProperties agentProperties) {
        this.root = Path.of(agentProperties.workspaceRoot()).toAbsolutePath().normalize();
    }

    public Path root() {
        return root;
    }

    public Path resolve(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new WorkspaceViolationException("путь не указан");
        }
        Path candidate = Path.of(rawPath);
        Path resolved = (candidate.isAbsolute() ? candidate : root.resolve(candidate)).normalize();
        if (!resolved.startsWith(root))
            throw new WorkspaceViolationException(
                    "путь вне рабочей папки: %s (рабочая папка: %s)".formatted(resolved, root));
        try {
            Path realPath = resolved.toRealPath();
            if (!realPath.startsWith(root.toRealPath())) {
                throw new WorkspaceViolationException("путь ведёт наружу через симлинк: " + rawPath);
            }
        } catch (NoSuchFileException pathNotCreatedYet) {
            // Пути ещё нет: модель ошиблась именем или файл только предстоит создать.
            // Симлинка у несуществующего пути быть не может — проверять нечего.
        } catch (IOException cannotVerify) {
            throw new WorkspaceViolationException(
                    "не удалось проверить путь %s: %s".formatted(rawPath, cannotVerify));
        }
        return resolved;
    }

    public String relativize(Path path) {
        return root.relativize(path).toString().replace("\\", "/");
    }

    public class WorkspaceViolationException extends RuntimeException {
        public WorkspaceViolationException(String путьНеУказан) {
            super(путьНеУказан);
        }
    }


}
