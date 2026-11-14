package ru.harness.hud;

/**
 * Цвета красного интерфейса T-800.
 *
 * <p>Всё, что относится к внешнему виду, живёт в пакете {@code hud} и только здесь.
 */
final class Ansi {

    static final String RESET = "[0m";
    static final String RED = "[31m";
    static final String BRIGHT_RED = "[91m";
    static final String DIM_RED = "[2;31m";
    static final String BOLD = "[1m";

    private Ansi() {}

    static String red(String text) {
        return RED + text + RESET;
    }

    static String brightRed(String text) {
        return BRIGHT_RED + text + RESET;
    }

    static String dim(String text) {
        return DIM_RED + text + RESET;
    }

    static String bold(String text) {
        return BOLD + text + RESET;
    }
}
