package ru.harness.hud;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Одна строка boot-лога: часть тела, её статус и то, что за ней стоит.
 *
 * <p>Часть тела лежит в самой строке, поэтому наружу монитор отдаёт список строк,
 * а не карту: печатающему не нужно знать, по какому ключу строка лежала.
 *
 * <p>Статус строка не выбирает: его передаёт тот, кто осматривал подсистему.
 * Сегодня это всегда {@link BootStatus#ONLINE}, но решение «чем закончился осмотр»
 * принадлежит монитору, а не структуре данных — иначе новый статус пришлось бы
 * заводить здесь, где про подсистемы ничего не известно.
 *
 * <p>Множество компонентов изменяемое намеренно: одной части тела может
 * соответствовать несколько инструментов, и монитор наполняет строку по ходу
 * осмотра. {@link LinkedHashSet} сохраняет порядок, в котором компоненты нашлись.
 */
record BootLine(BodyPart part, BootStatus status, Set<String> components) {

    static BootLine of(BodyPart part, BootStatus status, String detail) {
        Set<String> components = new LinkedHashSet<>();
        components.add(detail);
        return new BootLine(part, status, components);
    }

    static BootLine empty(BodyPart bodyPart) {
        return new BootLine(bodyPart, BootStatus.ONLINE, new LinkedHashSet<>());
    }

}
