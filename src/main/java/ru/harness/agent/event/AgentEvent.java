package ru.harness.agent.event;

import java.time.Duration;

/**
 * sealed нужна, чтобы можно было реализовать в других пакетах
 */
public sealed interface AgentEvent {

    record TurnStarted(String userMessage) implements AgentEvent {
    }

    record AgentSpoke(String assistantMessage) implements AgentEvent {
    }

    record TurnFinished(Duration duration, int iteration) implements AgentEvent {
    }

     record ToolCallStarted(String toolName, String description) implements AgentEvent { }
     record ToolCallFinished(String toolName, boolean error, String preview, Duration duration) implements AgentEvent { }
     record TurnLimitReached(int maxTurns) implements AgentEvent { }
}
