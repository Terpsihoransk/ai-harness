package ru.harness.permission;

/**
 * Откуда пришло правило и куда сохраняем согласие человека.
 * Файлы читаем в этом порядке: APPLICATION → USER → PROJECT → LOCAL.
 * Правила накапливаются, режим берёт последний заданный; приоритета источник не даёт —
 * DENY любого источника сильнее ALLOW любого другого.
 * Сохранить согласие можно только в SESSION или LOCAL: в остальные файлы агент не пишет.
 */
public enum PermissionSource {

    /** application.yaml: правила, которые Spring связал с PermissionProperties. */
    APPLICATION,

    /** ~/.harness/permissions.yaml — настройки человека для всех его проектов. */
    USER,

    /** .harness/permissions.yaml — настройки этого проекта. */
    PROJECT,

    /** .harness/permissions.local.yaml — сюда уходит постоянное согласие. */
    LOCAL,

    /** Согласие этой сессии: живёт в памяти и исчезает с перезапуском. */
    SESSION
}