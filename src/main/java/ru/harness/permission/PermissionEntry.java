package ru.harness.permission;

/**
 * Правило вместе с его типом и источником. Пример: Bash(echo *) + ALLOW + local.
 * Источник — application, user, project, local или session. Он объясняет происхождение
 * и отличает согласие этой сессии, но не даёт правилу приоритета: DENY любого источника сильнее ALLOW.
 * Создают OrdersCollector и OrdersKeeper; читают PermissionEvaluator, PathInspector и CommandInspector.
 */
public record PermissionEntry(PermissionRule rule, PermissionBehavior behavior, PermissionSource source) {}