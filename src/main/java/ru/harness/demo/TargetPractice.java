package ru.harness.demo;

/**
 * Мишень для занятий. Агент правит <b>её</b>, а не боевой код.
 *
 * <p>Результат правки пишет языковая модель, и он разный при каждом прогоне.
 * Если бы агент правил настоящий класс, состояние проекта после демонстрации
 * было бы у всех своё. Мишень одна и та же у всех, а после прогона
 * возвращается одной командой:
 *
 * <pre>git checkout -- src/main/java/ru/harness/demo/TargetPractice.java</pre>
 *
 * <p>В работе агента класс не участвует: бином не является, из кода проекта
 * его никто не зовёт.
 */
public final class TargetPractice {

    private TargetPractice() {
    }

    /**
     * Имя пакета по пути к исходнику.
     *
     * <p>{@code "src/main/java/ru/harness/agent/Agent.java"} → {@code "ru.harness.agent"}
     *
     * @param sourcePath путь к java-файлу от корня проекта, через прямые слэши
     * @return имя пакета через точки
     */
    public static String packageOf(String sourcePath) {
        throw new UnsupportedOperationException("мишень для урока 5: метод ещё не реализован");
    }

}
