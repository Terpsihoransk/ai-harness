package ru.harness.agent;

import ru.harness.agent.event.AgentEvent;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;

/**
 * Ловит события агента, чтобы проверить, что ядро сообщает о фактах.
 * Заодно доказывает, что подписчиком может быть кто угодно — HUD тут не при чём.
 */
class RecordingEventPublisher implements ApplicationEventPublisher {

    private final List<AgentEvent> events = new ArrayList<>();

    @Override
    public void publishEvent(Object event) {
        if (event instanceof AgentEvent agentEvent) {
            events.add(agentEvent);
        }
    }

    List<AgentEvent> events() {
        return List.copyOf(events);
    }
}
