package ru.harness.agent.event;

import java.time.Duration;

/**
 * sealed - можем реализовывать интерфейс не только в этом пакете
 */
public sealed interface AgentEvent {

    record TurnStarted(String userMessage) implements AgentEvent { }
    record AgentSpoke(String assistantMessage) implements AgentEvent { }
    record TurnFinished(Duration duration) implements AgentEvent { }

}
