package ru.harness.tool.impl;

import java.nio.file.Path;
import java.util.Set;

/**
 * Папки, которых модель видеть не должна. Дело не в секретности, а в выдаче:
 * сортировка по свежести ставит артефакты сборки в начало списка, а агент
 * берёт первый результат.
 */
final class Ignored {

    private static final Set<String> DIRECTORIES =
            Set.of(".git", "target", "build", "node_modules", ".idea", ".mvn", "ollama");

    private Ignored() {}

    static boolean contains(Path root, Path candidate) {
        Path relative = root.relativize(candidate);
        for (Path segment : relative) {
            if (DIRECTORIES.contains(segment.toString())) {
                return true;
            }
        }
        return false;
    }

}
