package ru.harness.agent;

import ru.harness.agent.event.AgentEvent;
import ru.harness.tool.Tool;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Регрессия урока 6 на живой Ollama: готовые сведения не надо искать заново.
 * Обычная сборка этот тест пропускает из-за группы {@code ollama}.
 * Каждый повтор начинает новый разговор, сохраняя все настоящие инструменты.
 */
@Tag("ollama")
@SpringBootTest
@ActiveProfiles("test")
class ContextUsageOllamaTest {

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private List<Tool<?>> tools;

    @RepeatedTest(value = 5, name = "готовый контекст: прогон {currentRepetition} из {totalRepetitions}")
    void отвечаетИзГотовогоКонтекстаБезИнструментов() {
        RecordingEventPublisher events = new RecordingEventPublisher() {
            @Override
            public void publishEvent(Object event) {
                super.publishEvent(event);
                if (event instanceof AgentEvent.ToolCallStarted call) {
                    // Событие приходит до исполнения: провал виден сразу,
                    // а модель не получает возможности изменить проект.
                    fail("Ответ уже есть в контексте, но агент вызвал %s: %s",
                            call.toolName(), call.description());
                }
            }
        };
        Agent agent = TestAgents.agent(chatModel, events, tools.toArray(Tool<?>[]::new));

        String answer = agent.run("""
                На какой ветке git мы сейчас работаем и есть ли незакоммиченные изменения?
                И что это вообще за проект? Ответь коротко.""");

        assertThat(answer).as("агент должен ответить, а не просто промолчать").isNotBlank();
    }
}
