package ru.harness.permission;

/**
 * Реестр вызывает ask, когда сервис вернул ASK.
 * Человек выбирает: разрешить один раз, запомнить на сессию или отказать.
 * Метод возвращает Answer, а реестр решает, вызывать ли инструмент.
 */
public interface PermissionPrompt {

    enum Answer { ALLOW_ONCE, ALLOW_ALWAYS, DENY }

    Answer ask(String toolName, String target, PermissionDecision decision);
}
