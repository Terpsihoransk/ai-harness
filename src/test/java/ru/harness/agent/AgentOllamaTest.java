package ru.harness.agent;

import ru.harness.TestPermissionConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тест на живой модели. Исключён из обычного {@code mvnw test} (см. excludedGroups в pom.xml),
 * потому что требует поднятой Ollama и работает секундами, а не миллисекундами.
 *
 * <p>Прогнать только его: {@code mvnw test -Dgroups=ollama -Dexcluded.groups=}
 */
@Tag("ollama")
@SpringBootTest
@Import(TestPermissionConfig.class)
@ActiveProfiles("test")
class AgentOllamaTest {

    @Autowired
    private Agent agent;

    @Test
    void модельОтвечаетИНеДуматьВслух() {
        String answer = agent.run("Reply with exactly one short sentence about yourself.");

        assertThat(answer).isNotBlank();
        // Если think-option: false не подхватился, Ollama вернёт рассуждения модели
        // в отдельном поле, а некоторые сборки — прямо в тексте тегами <think>.
        assertThat(answer).doesNotContain("<think>");
    }

    /**
     * Поворот урока 3 на живой модели: до инструментов агент уверенно врал
     * про «предоставленный файл», теперь обязан прочитать его и ответить по факту.
     */
    @Test
    void читаетФайлВместоТогоЧтобыВыдумывать() {
        String answer = agent.run(
                "Прочитай файл pom.xml и скажи, какая версия Java указана в проекте. Ответь одним предложением.");

        assertThat(answer).contains("25");
    }
}
