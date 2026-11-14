package ru.harness.permission;

import ru.harness.tool.WorkspaceGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class OrdersCollector {
    // Начальные строки из application.yaml; Spring уже связал YAML с этим record.
    private final PermissionProperties properties;
    // Помогает превратить относительные пути в абсолютные; разрешения сам не решает.
    private final WorkspaceGuard guard;
    // Пути трёх дополнительных источников: user, project и local.
    private final Map<PermissionSource, Path> files;

    @Autowired
    public OrdersCollector(PermissionProperties properties, WorkspaceGuard guard) {
        // В рабочем приложении используем стандартные пути; тесты могут передать свои через второй конструктор.
        this(properties, guard, Map.of(
                PermissionSource.USER, Path.of(System.getProperty("user.home"), ".harness", "permissions.yaml"),
                PermissionSource.PROJECT, guard.root().resolve(".harness/permissions.yaml"),
                PermissionSource.LOCAL, guard.root().resolve(".harness/permissions.local.yaml")));
    }

    public OrdersCollector(PermissionProperties properties, WorkspaceGuard guard, Map<PermissionSource, Path> files) {
        this.properties = properties;
        this.guard = guard;
        // Фиксируем карту путей, чтобы снаружи её не поменяли после создания брифинга.
        this.files = Map.copyOf(files);
    }

    public ToolPermissionContext load() {
        ArrayList<PermissionEntry> rules = new ArrayList<>();
        ArrayList<Path> directories = new ArrayList<>();
        addRules(rules, properties.allow(), PermissionBehavior.ALLOW, PermissionSource.APPLICATION);
        addRules(rules, properties.deny(), PermissionBehavior.DENY, PermissionSource.APPLICATION);
        addRules(rules, properties.ask(), PermissionBehavior.ASK, PermissionSource.APPLICATION);

        properties.additionalDirectories().forEach(path -> directories.add(guard.resolve(path)));

        PermissionMode mode = properties.mode();

        for (PermissionSource source : List.of(PermissionSource.USER, PermissionSource.PROJECT, PermissionSource.LOCAL)) {
            Path file = files.get(source);
            // В тестах карта может содержать не все источники; отсутствующий источник пропускаем.
            if (file == null) continue;
            // Читаем один YAML. Если файла нет, получим пустую карту и сохраним прежний режим.
            Map<String, Object> settings = read(file);
            // Списки allow/ask/deny добавляем к уже собранным правилам; не заменяем и не сортируем по узости шаблона.
            for (PermissionBehavior behavior : PermissionBehavior.values())
                addRules(rules, strings(settings.get(behavior.name().toLowerCase(Locale.ROOT))), behavior, source);
            // Дополнительные рабочие каталоги тоже собираем из всех источников.
            for (String directory : strings(settings.get("additional-directories")))
                directories.add(guard.resolve(directory));
            // Только явно заданный mode заменяет предыдущий; файл с одними правилами режим не меняет.
            if (settings.get("mode") != null) mode = PermissionMode.valueOf(settings.get("mode").toString());
        }


        return new ToolPermissionContext(mode, rules, directories);

    }

    private static List<String> strings(Object value) {
        // Отсутствующий allow/ask/deny или список каталогов считаем пустым списком.
        if (value == null) return List.of();
        // В YAML здесь нужен список: строку вместо списка считаем ошибкой настройки.
        if (!(value instanceof List<?> list))
            throw new IllegalArgumentException("ожидался список правил, получено: " + value);
        // Превращаем элементы YAML-списка в строки перед разбором правил.
        return list.stream().map(Object::toString).toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> read(Path file) {
        // Наличие дополнительных файлов необязательно; отсутствующий файл ничего не добавляет.
        if (!Files.exists(file)) return new LinkedHashMap<>();
        // Открываем YAML; try закроет файл и при успешном чтении, и при ошибке.
        try (var input = Files.newBufferedReader(file)) {
            // SnakeYAML превращает содержимое файла в Java-объекты: карту, списки и строки.
            Object value = new Yaml().load(input);
            // Пустой файл YAML не добавляет настроек.
            if (value == null) return new LinkedHashMap<>();
            // Верхний уровень файла должен быть картой с ключами allow/deny/ask/mode.
            if (!(value instanceof Map<?, ?>))
                throw new IllegalArgumentException("настройки должны быть YAML-объектом: " + file);
            // Карта сохраняет остальные настройки, когда дописываем локальное правило.
            return new LinkedHashMap<>((Map<String, Object>) value);
        } catch (IOException e) {
            // Сообщаем, какой файл не удалось прочитать; молча терять его запреты нельзя.
            throw new IllegalStateException("не прочитать настройки " + file, e);
        }
    }

    private static void addRules(List<PermissionEntry> out, List<String> values, PermissionBehavior behavior, PermissionSource source) {
        // Разбираем Tool(pattern), сохраняем тип и источник.
        for (String line : values) out.add(new PermissionEntry(PermissionRule.parse(line), behavior, source));
    }

    void saveLocalRule(PermissionEntry entry) {
        Path file = files.get(PermissionSource.LOCAL);
        if (file == null) throw new IllegalStateException("не указан файл локальных разрешений");
        Map<String, Object> settings = read(file);
        String key = entry.behavior().name().toLowerCase(Locale.ROOT);
        List<String> rules = new ArrayList<>(strings(settings.get(key)));
        String rule = entry.rule().toString();
        if (rules.contains(rule)) return;
        rules.add(rule);
        settings.put(key, rules);

        // Записываем рядом и заменяем файл после успешной записи.
        try {
            Files.createDirectories(file.getParent());
            Path temporary = Files.createTempFile(file.getParent(), "permissions-", ".yaml");
            try {
                Files.writeString(temporary, new Yaml().dump(settings));
                try {
                    Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException e) {
            throw new IllegalStateException("не сохранить настройки " + file, e);
        }
    }

}