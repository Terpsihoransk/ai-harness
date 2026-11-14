package ru.harness.permission;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Инспектирует командную строку. BashTool передаёт сюда её текст, метод check возвращает Decision.
 * Порядок: DENY → явный ASK → цепочка && по частям → неразобранный синтаксис → опасная операция → ALLOW → только чтение → обычный ASK.
 * Это учебный разбор простых команд, а не парсер shell: всё, чего не понимаем, отдаём человеку.
 * Команды здесь не выполняем и человека не спрашиваем: Decision уходит через Tool в PermissionEvaluator и PermissionGate.
 */
public final class CommandInspector {
    private CommandInspector() {}

    // Небольшой список команд, которые в учебном агенте считаем чтением без вопроса.
    private static final Set<String> READ_ONLY = Set.of(
            "pwd", "cd", "ls", "ls -la", "dir", "java -version", "git status", "git status --short", "git status --porcelain", "git log --oneline", "git diff",
            "git diff --stat", "git branch --show-current");
    // Эти фрагменты команды требуют подтверждения даже при наличии обычного ALLOW.
    private static final List<String> DANGEROUS = List.of(
            "rm -rf", "rm -fr", "git reset --hard", "git clean -f", "git checkout --", "git restore ", "push --force", "push -f", "drop table", "drop database", "shutdown", "kill -9", "mkfs",
            "format ");
    // Символы shell, за которыми может прятаться вторая команда или запись в файл.
    private static final String UNPARSED_SYNTAX = "&|;\r\n`$()<>%!";



    public static PermissionDecision check(String command, ToolPermissionContext settings) {
        if (command == null || command.isBlank()) return PermissionDecision.deny("не указана команда");
        command = command.strip();
        PermissionEntry deny = findRule(command, settings, PermissionBehavior.DENY);
        if (deny != null) return PermissionDecision.deny("запрещено правилом " + deny.rule());
        PermissionEntry ask = findRule(command, settings, PermissionBehavior.ASK);
        if (ask != null) return PermissionDecision.askAlways("вопрос по правилу " + ask.rule());
        if (command.contains("&&")) return checkChain(command, settings);
        if (hasUnparsedSyntax(command)) return PermissionDecision.askAlways("составная команда, подстановка или перенаправление: нужно подтверждение");
        String dangerous = findDangerous(command);
        if (dangerous != null) return PermissionDecision.askAlways("необратимая операция: " + dangerous);
        // В ОРИГИНАЛЕ здесь проверяются пути в аргументах: mkdir, cp, rm и подобные команды проходят
        // те же файловые проверки, что Write и Edit, — запрет Edit(.env) останавливает и rm .env.
        // У нас этого нет: команда сравнивается с правилами только как текст.
        PermissionEntry allow = findRule(command, settings, PermissionBehavior.ALLOW);
        if (allow != null) return PermissionDecision.allow("разрешено правилом " + allow.rule());
        if (READ_ONLY.contains(command)) return PermissionDecision.allow("команда только читает");
        return PermissionDecision.ask("выполнение команды в системе").withSuggestions(rememberCommand(command));
    }

    /**
     * Предлагаем запомнить эту команду в local. Звёздочка в конце сделала бы правило префиксом
     * (Bash(ls *) разрешил бы любой ls), поэтому такую команду запомнить не предлагаем.
     */
    private static List<PermissionUpdate> rememberCommand(String command) {
        if (command.endsWith("*")) return List.of();
        return List.of(PermissionUpdate.rule(new PermissionRule("Bash", command), PermissionBehavior.ALLOW, PermissionSource.LOCAL));
    }


    /** Возвращаем найденный опасный фрагмент, например "git reset --hard"; null — такого нет. */
    private static @Nullable String findDangerous(String command) {
        String lower = command.toLowerCase(Locale.ROOT);
        return DANGEROUS.stream().filter(lower::contains).findFirst().orElse(null);
    }

    private static boolean hasUnparsedSyntax(String command) {
        return command.chars().anyMatch(c -> UNPARSED_SYNTAX.indexOf(c) >= 0);
    }

    /** Ищем правило Bash нужного типа: точная строка или префикс с конечной *. null — такого нет. */
    private static @Nullable PermissionEntry findRule(String command, ToolPermissionContext settings, PermissionBehavior behavior) {
        return settings.ruleEntries().stream()
                .filter(e -> e.behavior() == behavior && e.rule().appliesTo("Bash") && e.rule().argumentPattern() != null)
                .filter(e -> matches(command, e.rule().argumentPattern()))
                .findFirst().orElse(null);
    }

    /** git status* совпадёт с git status --short; без звёздочки сравниваем строку целиком. */
    private static boolean matches(String command, String pattern) {
        pattern = pattern.strip();
        return pattern.endsWith("*") ? command.startsWith(pattern.substring(0, pattern.length() - 1)) : command.equals(pattern);
    }


    /**
     * Цепочка a && b && c: каждую часть проверяем тем же check. Запрет любой части запрещает всё,
     * обязательный вопрос любой части остаётся обязательным, разрешена строка только когда разрешены все части.
     * В ОРИГИНАЛЕ так разбирается любая составная команда — ; | || и подстановки: парсер shell делит
     * строку на подкоманды, и каждая сверяется с запретами. У нас по частям идёт только &&.
     */
    private static PermissionDecision checkChain(String command, ToolPermissionContext settings) {
        // С кавычками и экранированием && может оказаться внутри строки: делить такую команду нельзя.
        if (command.contains("\"") || command.contains("'") || command.contains("\\")) return PermissionDecision.askAlways("сложный разбор команды: нужно подтверждение");
        boolean allAllowed = true;
        PermissionDecision requiredQuestion = null;
        for (String part : command.split("&&", -1)) {
            PermissionDecision decision = check(part, settings);
            if (decision.behavior() == PermissionBehavior.DENY) return decision;
            if (decision.askAlways()) requiredQuestion = decision;
            allAllowed &= decision.behavior() == PermissionBehavior.ALLOW;
        }
        if (requiredQuestion != null) return requiredQuestion;
        return allAllowed ? PermissionDecision.allow("все части команды разрешены") : PermissionDecision.ask("вопрос для всей строки Bash");
    }
}