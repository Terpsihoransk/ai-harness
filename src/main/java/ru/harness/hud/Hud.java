package ru.harness.hud;

import org.springframework.ai.chat.messages.Message;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ru.harness.agent.Agent;
import ru.harness.agent.event.AgentEvent;
import ru.harness.context.TokenEstimator;
import ru.harness.permission.PermissionEvaluator;
import ru.harness.tool.Tool;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static ru.harness.hud.BodyPart.forToolName;
import static ru.harness.hud.LanguageUtils.plural;

@Component
public class Hud {

    private final MachineViewPrinter machinePrinter;
    private final Agent agent;
    private final List<Tool<?>> tools;
    private final PermissionEvaluator permissions;

    public Hud(MachineViewPrinter machinePrinter, Agent agent, List<Tool<?>> tools, PermissionEvaluator permissions) {
        this.machinePrinter = machinePrinter;
        this.agent = agent;
        this.tools = tools;
        this.permissions = permissions;
    }

    @EventListener(ApplicationStartedEvent.class)
    public void onApplicationStarted(ApplicationStartedEvent event) {
        machinePrinter.showBootLog(collectBootLines(event.getApplicationContext().getBeanDefinitionCount()));
    }

    private List<BootLine> collectBootLines(int beanDefinitionCount) {
        Map<BodyPart, BootLine> lines = new EnumMap<>(BodyPart.class);

        lines.put(BodyPart.POWER_CELL, BootLine.of(BodyPart.POWER_CELL, BootStatus.ONLINE,
                "спринг контекст " + plural(beanDefinitionCount, "бин", "бина", "бинов")));
        lines.put(BodyPart.NEUROPROCESSOR,
                BootLine.of(BodyPart.NEUROPROCESSOR, BootStatus.ONLINE, agent.chatModel()));
        lines.put(BodyPart.MEMORY_BANK, BootLine.of(BodyPart.MEMORY_BANK, BootStatus.ONLINE,
                agent.conversation().getClass().getSimpleName()));
        lines.put(BodyPart.DIAGNOSTIC_BUS, BootLine.of(BodyPart.DIAGNOSTIC_BUS, BootStatus.ONLINE,
                "Agent Events: " + AgentEvent.class.getPermittedSubclasses().length));
        for (Tool<?> tool : tools) {
            BodyPart part = forToolName(tool.name());
            BootLine bootLine = lines.computeIfAbsent(part, BootLine::empty);
            bootLine.components().add(tool.name());
            if (permissions.isLocked(tool)) {
                lines.put(part, new BootLine(part, BootStatus.LOCKED, bootLine.components()));
            }
        }
        if (!tools.isEmpty()) {
            lines.put(BodyPart.TOOL_BUS, BootLine.of(BodyPart.TOOL_BUS, BootStatus.ONLINE,
                    plural(tools.size(), "инструмент", "инструмента", "инструментов")));
        }


        return List.copyOf(lines.values());

    }

    @EventListener
    public void onTurnStarted(AgentEvent.TurnStarted event) {
        String userInput = event.userMessage();
        machinePrinter.showOrder(userInput);
        List<Message> promptMessages = agent.conversation().toPromptMessages();
        machinePrinter.showContext(promptMessages, TokenEstimator.estimate(promptMessages));

    }

    @EventListener
    public void onTurnFinished(AgentEvent.TurnFinished event) {
        machinePrinter.showTurnFinished(event.duration(), event.iteration());
    }

    @EventListener
    public void onToolCallStarted(AgentEvent.ToolCallStarted event) {
        machinePrinter.showToolCallStarted(event.description());
    }

    /**
     * PermissionGate прислал событие о вопросе. Hud показывает сообщение; ответ читает консоль.
     */
    @EventListener
    public void onPermissionRequested(AgentEvent.PermissionRequested event) {
        machinePrinter.showPermissionRequested(event.toolName(), event.target(), event.reason());
    }

    /**
     * PermissionGate прислал ответ: при согласии Hud показывает ARMED, при отказе — сообщение об отказе.
     */
    @EventListener
    public void onPermissionResolved(AgentEvent.PermissionResolved event) {
        machinePrinter.showPermissionResolved(forToolName(event.toolName()), event.granted());
    }

    @EventListener
    public void onToolCallFinished(AgentEvent.ToolCallFinished event) {
        machinePrinter.showToolCallFinished(event.error(), event.preview(), event.duration());
    }

    @EventListener
    public void onTurnLimitReached(AgentEvent.TurnLimitReached event) {
        machinePrinter.showTurnLimitReached(event.maxTurns());
    }


    //    @EventListener
    public void onAgentSpokeEvent(AgentEvent.AgentSpoke event) {
        String assistantMessage = event.assistantMessage();
        boolean containsRussian = java.util.regex.Pattern
                .compile("[А-Яа-яЁё]")
                .matcher(assistantMessage)
                .find();

        if (containsRussian) {
            System.err.println("НЕ ГОВОРИ СО МНОЙ ПО РУССКИ");
            throw new RuntimeException(assistantMessage);
        }
    }

}
