package ru.harness.permission;

import org.jspecify.annotations.Nullable;
import ru.harness.tool.WorkspaceGuard;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Инспектирует один путь по правилам. Read/Grep/Glob вызывают checkRead; Edit/Write — checkWrite;
 * Bash проверяет здесь же пути своих аргументов.
 * Порядок: сетевой путь → DENY → явный ASK → защита настроек → рабочие каталоги и режим → ALLOW → обычный ASK.
 * Здесь работают только правила с путём: Read(src/**), Edit(.env). Правило на весь инструмент
 * (Read, Edit, Grep) проверяет PermissionEvaluator по имени инструмента.
 * Чтение подчиняется правилам Read, запись — правилам Edit; Write пишет по правилам Edit.
 * BYPASS здесь не учитываем: обычный ASK снимет PermissionEvaluator, а защиту настроек режим не снимает.
 * rawPath — путь из Input, например src/App.java; подпись "[EDIT] src/App.java" сюда не передаём.
 */
public final class PathInspector {
    private PathInspector() {
    }

    // Эти каталоги содержат настройки: обычного ALLOW и режима на запись в них недостаточно.
    private static final Set<String> PROTECTED_DIRECTORIES = Set.of(".git", ".idea", ".vscode", ".claude", ".harness", ".mvn", ".husky", ".cargo", ".devcontainer", ".yarn");
    // Эти файлы тоже считаем настройками, даже если они лежат вне служебного каталога.
    private static final Set<String> PROTECTED_FILES = Set.of(
            ".gitconfig", ".gitmodules", ".bashrc", ".bash_profile", ".bash_login", ".bash_aliases", ".bash_logout", ".zshrc", ".zprofile", ".zshenv", ".zlogin", ".zlogout",
            ".envrc", ".profile", ".npmrc", ".yarnrc", ".yarnrc.yml", ".pnp.cjs", ".pnp.loader.mjs", ".pnpmfile.cjs", "bunfig.toml", ".bunfig.toml", ".bazelrc",
            ".bazelversion", ".bazeliskrc", ".pre-commit-config.yaml", "lefthook.yml", "lefthook.yaml", ".lefthook.yml", ".lefthook.yaml", ".mcp.json", ".claude.json",
            ".ripgreprc", "pyrightconfig.json", "maven-wrapper.properties", "gradle-wrapper.properties");


    public static PermissionDecision checkRead(WorkspaceGuard guard, String rawPath, ToolPermissionContext settings) {
        return check(guard, rawPath, settings, false);
    }

    public static PermissionDecision checkWrite(WorkspaceGuard guard, String rawPath, ToolPermissionContext settings) {
        return check(guard, rawPath, settings, true);
    }

    private static PermissionDecision check(WorkspaceGuard guard, String rawPath, ToolPermissionContext settings, boolean write) {
        if (isNetwork(rawPath)) return PermissionDecision.askAlways("сетевой путь: нужно подтверждение до обращения к сети");
        Path path = guard.resolve(rawPath);
        PermissionEntry deny = find(guard, path, settings, PermissionBehavior.DENY, write);
        if (deny != null) return PermissionDecision.deny("запрещено правилом " + deny.rule());
        PermissionEntry ask = find(guard, path, settings, PermissionBehavior.ASK, write);
        if (ask != null) return PermissionDecision.askAlways("вопрос по правилу " + ask.rule());
        if (write && isProtected(guard, path)) return PermissionDecision.askAlways("настройки Git, IDE или агента: " + rawPath);
        // В ОРИГИНАЛЕ здесь два исключения из защиты настроек. У нас их нет — в них некому писать.
        // 1. Служебный каталог сессии: свои планы, заметки и черновики агент пишет без вопроса.
        // 2. Согласие в диалоге на весь .claude/** снимает защиту с каталога агента до конца сессии.

        boolean inside = inWorkingDirectories(guard, path, settings);
        if (inside && (!write || settings.mode() == PermissionMode.ACCEPT_EDITS)) return PermissionDecision.allow(write ? "acceptEdits внутри рабочей области" : "чтение внутри рабочей области");
        PermissionEntry allow = find(guard, path, settings, PermissionBehavior.ALLOW, write);
        if (allow != null) return PermissionDecision.allow("разрешено правилом " + allow.rule());
        return PermissionDecision.ask(inside ? "изменение файла" : "путь вне рабочих каталогов: " + rawPath)
                .withSuggestions(suggestions(path, write, inside));
    }

    /**
     * Что предложить запомнить. Всегда на сессию и всегда каталог, а не один файл: правило на один
     * файл не избавит от вопроса на следующем. Чтение — Read(//каталог/**), запись — Edit(//каталог/**),
     * а снаружи рабочей области запись добавляет ещё и сам каталог: правило Edit не разрешает ни Bash,
     * ни чтение. Шаблон абсолютный: относительный /** совпал бы и с путём за пределами проекта —
     * relativize отдаёт такой путь как ../файл. Корень диска и каталог с * или ? в имени не предлагаем.
     */
    private static List<PermissionUpdate> suggestions(Path path, boolean write, boolean inside) {
        List<PermissionUpdate> updates = new ArrayList<>();
        Path directory = Files.isDirectory(path) ? path : path.getParent();
        if (directory == null || directory.getParent() == null || directory.toString().matches(".*[*?].*")) return updates;
        String folder = "//" + directory.toString().replace('\\', '/') + "/**";
        updates.add(PermissionUpdate.rule(new PermissionRule(write ? "Edit" : "Read", folder), PermissionBehavior.ALLOW, PermissionSource.SESSION));
        if (write && !inside) updates.add(PermissionUpdate.directory(directory.toString()));
        return updates;
    }

    private static boolean isNetwork(String rawPath) {
        return rawPath != null && (rawPath.startsWith("\\\\") || rawPath.startsWith("//"));
    }

    private static boolean isProtected(WorkspaceGuard guard, Path path) {
        // Настройки считаем защищёнными, даже если к ним ведёт ссылка из обычного каталога.
        return guard.pathsForCheck(path).stream().anyMatch(p -> {
            // Смотрим каждый сегмент: src/.git/config тоже находится внутри .git.
            for (int i = 0; i < p.getNameCount(); i++) {
                String segment = p.getName(i).toString().toLowerCase(Locale.ROOT);
                // Репозиторий внутри .claude/worktrees — обычная рабочая область.
                if (segment.equals(".claude") && i + 1 < p.getNameCount() && p.getName(i + 1).toString().equalsIgnoreCase("worktrees")) continue;
                if (PROTECTED_DIRECTORIES.contains(segment)) return true;
                // Отдельно узнаём пользовательские настройки .config/git.
                if (segment.equals(".config") && i + 1 < p.getNameCount() && p.getName(i + 1).toString().equalsIgnoreCase("git")) return true;
            }
            // Даже без служебного каталога имя вроде .bashrc или .gitconfig требует защиты.
            return p.getFileName() != null && PROTECTED_FILES.contains(p.getFileName().toString().toLowerCase(Locale.ROOT));
        });
    }

    private static boolean inWorkingDirectories(WorkspaceGuard guard, Path path, ToolPermissionContext settings) {
        List<Path> roots = new ArrayList<>(settings.additionalWorkingDirectories());
        roots.add(guard.root());
        // И ссылка, и её цель должны лежать хотя бы в одном разрешённом рабочем каталоге.
        return guard.pathsForCheck(path).stream()
                .allMatch(p -> roots.stream().anyMatch(r -> p.startsWith(r.toAbsolutePath().normalize()) || p.startsWith(guard.realPath(r))));
    }

    private static @Nullable PermissionEntry find(WorkspaceGuard guard, Path path, ToolPermissionContext settings, PermissionBehavior behavior, boolean write) {
        // Перебираем объединённые правила; более узкий шаблон не получает приоритета.
        for (PermissionEntry entry : settings.ruleEntries()) {
            if (entry.behavior() == behavior && matches(guard, path, entry, write)) return entry;
        }
        return null;
    }

    private static boolean matches(WorkspaceGuard guard, Path path, PermissionEntry entry, boolean write) {
        // Чтение — правила Read, запись — правила Edit. Правило без пути относится к инструменту, а не к файлу.
        String pattern = entry.rule().argumentPattern();
        if (pattern == null || !entry.rule().appliesTo(write ? "Edit" : "Read")) return false;
        // ALLOW смотрит только на путь, который назван: разрешение ссылки не распространяется на чужую цель.
        if (entry.behavior() == PermissionBehavior.ALLOW) return PermissionPathMatcher.matches(guard.root(), path, pattern);
        // DENY и ASK срабатывают и по ссылке, и по её настоящему адресу: через alias запрет не обойти.
        return guard.pathsForCheck(path).stream().anyMatch(p -> PermissionPathMatcher.matches(guard.root(), p, pattern));
    }

}







