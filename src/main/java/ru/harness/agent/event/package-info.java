/**
 * По умолчанию всё в пакете не-null. Аннотация из JSpecify — тот же диалект,
 * на котором говорят Spring Framework 7 и Spring AI 2, поэтому наши переопределения
 * их методов совпадают по контракту. Исключения помечаются {@code @Nullable} точечно.
 */
@NullMarked
package ru.harness.agent.event;

import org.jspecify.annotations.NullMarked;
