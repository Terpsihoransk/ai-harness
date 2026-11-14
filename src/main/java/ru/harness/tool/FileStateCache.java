package ru.harness.tool;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FileStateCache {

    public record FileState(String content, long timestamp) {
    }

    private final Map<Path, FileState> states = new ConcurrentHashMap<>();

    public void remember(Path file) {
        Path key = file.normalize();
        try {
            states.put(key, new FileState(Files.readString(file), modifiedAt(file)));

        } catch (IOException e) {
            // Прочитать не удалось — значит и помнить нечего: пусть следующая
            // правка честно скажет «файл не прочитан», а не сверяется с пустотой.
            states.remove(key);
        }
    }

    public @Nullable FileState get(Path file) {
        return states.get(file.normalize());
    }

    public @Nullable String whyCannotWrite(Path file) {
        if (!Files.exists(file)) {
            return null; // файла ещё нет: читать было нечего, затирать тоже
        }
        FileState known = get(file);
        if (known == null) {
            return "файл не был прочитан. Прочитай его инструментом Read и повтори правку";
        }
        if (modifiedAt(file) > known.timestamp() && !sameContentOnDisk(file, known)) {
            return "файл изменился с момента чтения. Прочитай его инструментом Read заново и повтори правку";
        }
        return null;
    }

    private boolean sameContentOnDisk(Path file, FileState known) {
        try {
            return Files.readString(file).equals(known.content());
        } catch (IOException cannotRead) {
            return false;
        }
    }

    private static long modifiedAt(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException cannotRead) {
            return 0L;
        }
    }

}
