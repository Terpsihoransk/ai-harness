package ru.harness.context;

import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * Грубая оценка размера контекста в токенах.
 *
 * <p>Формула простая: длина в символах, делённая на четыре. Выглядит несерьёзно
 * для системы, которая на этом основании будет выбрасывать историю диалога
 * в уроке 9, — и тем не менее так считают и промышленные агенты.
 *
 * <p>Причина в том, что точный ответ стоит дорого, а нужен он не для точности.
 * Настоящий счёт токенов — это либо запрос к провайдеру, то есть сетевой вызов
 * перед каждым сетевым вызовом, либо свой токенизатор, который у каждой модели
 * свой. Оценка отвечает на вопрос «пора сжимать или ещё нет», и промах
 * на десять процентов там ничего не решает: порог берётся с запасом.
 *
 * <p>Кириллица разбивается мельче латиницы, так что на русском диалоге оценка
 * занижена. Для нашей цели это безопасная сторона ошибки только наполовину:
 * сжатие начнётся позже, чем следовало бы. Поэтому порог и стоит с запасом.
 */
public final class TokenEstimator {

    private static final int CHARS_PER_TOKEN = 4;

    private TokenEstimator() {}

    public static int estimate(String text) {
        return text == null ? 0 : Math.round((float) text.length() / CHARS_PER_TOKEN);
    }

    /** Оценка всего, что уедет в модель одним запросом. */
    public static int estimate(List<Message> messages) {
        int total = 0;
        for (Message message : messages) {
            total += estimate(message.getText());
        }
        return total;
    }
}
