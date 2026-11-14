package ru.harness.permission;

import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * PathInspector передаёт сюда путь и аргумент правила Read/Edit. Возвращаем только true/false.
 * src/** и /src/** отсчитываем от проекта, ~/ — от дома, // — абсолютный путь. Подписи [EDIT] здесь нет.
 */
public final class PermissionPathMatcher {
    private PermissionPathMatcher() {}

    public static boolean matches(Path root, Path path, String pattern) {
        // И путь, и шаблон сравниваем с /, независимо от разделителя текущей ОС.
        String glob = pattern.replace('\\', '/');
        // Двойной / в правиле означает абсолютный путь, например //C:/project/src/**.
        if (glob.startsWith("//")) return globMatches(glob.substring(2), slashes(path.toAbsolutePath().normalize()));
        // Шаблон ~/... отсчитываем от домашнего каталога пользователя.
        if (glob.startsWith("~/")) return globMatches(glob.substring(2), relative(Path.of(System.getProperty("user.home")), path));
        // Всё остальное — от проекта: src/**, /src/** и ./src/** означают одно и то же.
        return globMatches(glob.replaceFirst("^/", "").replaceFirst("^\\./", ""), relative(root, path));
    }

    /** Путь относительно base с разделителем /. null — путь на другом диске, относительным его не сделать. */
    private static @Nullable String relative(Path base, Path path) {
        if (!base.getRoot().equals(path.getRoot())) return null;
        return slashes(base.relativize(path));
    }

    private static String slashes(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static boolean globMatches(String glob, @Nullable String candidate) {
        if (candidate == null) return false;
        // На Windows сравниваем без учёта регистра; на других ОС регистр сохраняем.
        int flags = System.getProperty("os.name").toLowerCase().contains("win") ? Pattern.CASE_INSENSITIVE : 0;
        return Pattern.compile(toRegex(glob), flags).matcher(candidate).matches();
    }

    /** Превращаем шаблон в регулярное выражение: ** — любые каталоги, * — внутри одного имени, ? — один символ. */
    private static String toRegex(String glob) {
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            // Две звёздочки могут захватить несколько вложенных каталогов.
            if (c == '*' && i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                i++;
                // Сочетание **/ допускает и отсутствие промежуточного каталога: **/App.java подходит к App.java.
                if (i + 1 < glob.length() && glob.charAt(i + 1) == '/') {
                    i++;
                    regex.append("(?:.*/)?");
                // Без следующего / сочетание ** совпадает с любым остатком пути.
                } else regex.append(".*");
            // Одна * совпадает только внутри одного сегмента, без перехода через /.
            } else if (c == '*') regex.append("[^/]*");
            // Знак ? совпадает с одним символом имени, кроме /.
            else if (c == '?') regex.append("[^/]");
            // Остальные символы буквальные: точка не должна стать оператором регулярного выражения.
            else regex.append(Pattern.quote(String.valueOf(c)));
        }
        // Правило на каталог распространяем и на его содержимое; у конечного /** это уже учтено.
        if (!glob.endsWith("/**")) regex.append("(?:/.*)?");
        return regex.append('$').toString();
    }
}
