package ru.harness.permission;

/** Пользователь выбирает этот режим в YAML. */
public enum PermissionMode {
    /** Если готового разрешения нет, спросить человека. */
    DEFAULT,

    /** Обычные правки Edit/Write разрешить. Команды проверять как в DEFAULT. */
    ACCEPT_EDITS,

    /** Обычные вопросы пропустить. Запреты и askAlways оставить. */
    BYPASS_PERMISSIONS
}
