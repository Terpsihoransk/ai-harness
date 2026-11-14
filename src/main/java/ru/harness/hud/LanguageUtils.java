package ru.harness.hud;

/**
 * Русский язык там, где число превращается в текст.
 *
 * <p>Ни к метафоре, ни к оформлению отношения не имеет: «2 бина» и «5 бинов» —
 * это грамматика, а не цвет. Поэтому утилита, а не часть экрана.
 */
final class LanguageUtils {

    private LanguageUtils() {}

    /** 1 бин, 2 бина, 5 бинов. */
    static String plural(int count, String one, String few, String many) {
        int lastTwo = count % 100;
        int last = count % 10;
        if (lastTwo >= 11 && lastTwo <= 14 || last == 0 || last >= 5) {
            return count + " " + many;
        }
        return count + " " + (last == 1 ? one : few);
    }
}
