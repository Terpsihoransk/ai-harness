package ru.harness.demo;

/**
 * Мишень для занятий. Агент правит <b>её</b>, а не боевой код.
 *
 */
public final class TargetPractice {

    private TargetPractice() {
    }

    /**
     * Имя пакета по пути к исходнику.
     *
     * <p>{@code "ru/harness/demo/TargetPractice.java"} → {@code "ru.harness.demo"}
     *
     * @param sourcePath путь к java-файлу от корня проекта, через прямые слэши
     * @return локация источника кода
     */
    public static String packageOf(String sourcePath) {
        throw new UnsupportedOperationException("мишень для урока 5: метод ещё не реализован");
    }

}
