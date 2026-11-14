package ru.harness.permission;

import java.util.List;

/**
 * Решение по одному вызову: ALLOW — выполнить, DENY — отказать, ASK — спросить.
 * Инструмент или PermissionService возвращает этот record реестру.
 * При ASK флаг true позволяет снять вопрос разрешением или режимом; false — нет.
 * Record не меняем: для другого решения создаём новый.
 */
public record PermissionDecision(PermissionBehavior behavior, String reason, boolean askAlways,
                                 List<PermissionUpdate> suggestions) {
    public PermissionDecision {
        // Фиксируем варианты: диалог выберет из них, но не поменяет список проверки.
        suggestions = List.copyOf(suggestions);
    }

    public static PermissionDecision allow(String reason) {
        // Готовое разрешение на этот вызов; Gate вернёт его реестру без диалога.
        return new PermissionDecision(PermissionBehavior.ALLOW, reason, false, List.of());
    }

    public static PermissionDecision ask(String reason) {
        // Обычный вопрос: PermissionEvaluator ещё может снять его режимом или разрешением на весь Tool.
        return new PermissionDecision(PermissionBehavior.ASK, reason, false, List.of());
    }

    /** При таком ASK всегда спрашиваем человека, даже в BYPASS_PERMISSIONS и при разрешении на весь Tool. */
    public static PermissionDecision askAlways(String reason) {
        // Обязательный вопрос: проверки возвращают его раньше bypass и ALLOW. Ответ человека всё ещё может разрешить вызов.
        return new PermissionDecision(PermissionBehavior.ASK, reason, true, List.of());
    }

    public static PermissionDecision deny(String reason) {
        // Отказ завершает проверку. true здесь не снимает DENY: overridable учитываем только у ASK.
        return new PermissionDecision(PermissionBehavior.DENY, reason, false, List.of());
    }
    /** Тот же ответ с вариантами, которые диалог предложит запомнить. */
    public PermissionDecision withSuggestions(List<PermissionUpdate> updates) {
        return new PermissionDecision(behavior, reason, askAlways, updates);
    }



}