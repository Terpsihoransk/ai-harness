package ru.harness.tool;

import org.springframework.stereotype.Component;
import ru.harness.agent.AgentProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;

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
        // Без пути нельзя ни сравнить правило, ни вызвать файловую операцию.
        if (rawPath == null || rawPath.isBlank()) throw new WorkspaceViolationException("путь не указан");
        // Раскрываем ~/ относительно домашнего каталога пользователя.
        if (rawPath.startsWith("~/"))
            return Path.of(System.getProperty("user.home")).resolve(rawPath.substring(2)).normalize();
        Path candidate = Path.of(rawPath);
        // Абсолютный путь сохраняем; относительный присоединяем к проекту. .. нормализуем, но доступ не разрешаем.
        return (candidate.isAbsolute() ? candidate : root.resolve(candidate)).normalize();
    }

    /**
     * на вход /app/мяу_link/file.txt.
     * но link на сам мяу_link это симлинк и он ведет туда куда запрещено писать:  мяу_link = /var/docs
     * на выход мы должны отдать /var/docs/file.txt
     */
    public Path realPath(Path path) {
        Path absolutePath = path.toAbsolutePath().normalize();
        Path realPath = absolutePath;
        while (realPath != null && !Files.exists(realPath, LinkOption.NOFOLLOW_LINKS)) realPath = realPath.getParent();
        if (realPath == null) throw new WorkspaceViolationException("не удалось проверить путь: " + path);
        try {

            return realPath.toRealPath().resolve(realPath.relativize(absolutePath)).normalize();
        } catch (IOException e) {
            // Ошибка раскрытия пути превращается в отказ, а не в автоматическое разрешение.
            throw new WorkspaceViolationException("не удалось проверить путь " + path + ": " + e.getMessage());
        }
    }

    public List<Path> pathsForCheck(Path path) {
        Path real = realPath(path);
        return real.equals(path) ? List.of(path) : List.of(path, real);
    }



    public String relativize(Path path) {
        // Путь на другом диске или UNC относительным не сделать: показываем его целиком, а не падаем.
        if (!root.getRoot().equals(path.getRoot())) return path.toString().replace('\\', '/');
        return root.relativize(path).toString().replace("\\", "/");
    }


    public class WorkspaceViolationException extends RuntimeException {
        public WorkspaceViolationException(String путьНеУказан) {
            super(путьНеУказан);
        }
    }

}
