package ru.harness.permission;

/**
 * Что изменится в настройках, если человек согласится. Это не решение о выполнении Tool:
 * проверка кладёт варианты в ASK-решение, диалог показывает их человеку, PermissionGate поручает сохранение PermissionEvaluator.
 * Два вида изменения — две записи, поэтому пустых полей здесь нет и switch по ним проверяется исчерпывающе.
 * Согласие сессии — обычное правило: Read(//C:/shared/**) на сессию сравнивается так же, как такое же правило из YAML.
 * Режим здесь отсутствует: его задаёт человек в настройках, и в работе он не меняется.
 */
public sealed interface PermissionUpdate {
    /** SESSION — память до конца сессии; LOCAL — правило в .harness/permissions.local.yaml. */
    PermissionSource destination();

    /** Добавляем правило: например Bash(echo ok) + ALLOW. */
    record Rule(PermissionRule rule, PermissionBehavior behavior, PermissionSource destination) implements PermissionUpdate {}

    /** Добавляем рабочий каталог на сессию; это не ALLOW на любую Bash-команду. */
    record Directory(String path) implements PermissionUpdate {
        @Override
        public PermissionSource destination() {
            return PermissionSource.SESSION;
        }
    }

    static PermissionUpdate rule(PermissionRule rule, PermissionBehavior behavior, PermissionSource destination) {
        return new Rule(rule, behavior, destination);
    }

    static PermissionUpdate directory(String path) {
        return new Directory(path);
    }
}