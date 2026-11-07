package ru.harness.hud;

import org.springframework.ai.chat.messages.Message;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ru.harness.agent.Agent;
import ru.harness.agent.event.AgentEvent;
import ru.harness.context.TokenEstimator;

import java.util.ArrayList;
import java.util.List;

import static ru.harness.hud.BodyPart.DIAGNOSTIC_BUS;
import static ru.harness.hud.BodyPart.MEMORY_BANK;
import static ru.harness.hud.BodyPart.NEUROPROCESSOR;
import static ru.harness.hud.BodyPart.POWER_CELL;
import static ru.harness.util.LanguageUtils.plural;

@Component
public class Hud {

    private final MachineViewPrinter machinePrinter;
    private final Agent agent;

    public Hud(MachineViewPrinter machinePrinter, Agent agent) {
        this.machinePrinter = machinePrinter;
        this.agent = agent;
    }

    @EventListener(ApplicationStartedEvent.class)
    public void onApplicationStarted(ApplicationStartedEvent event) {
        machinePrinter.showBootLog(collectBootLines(event.getApplicationContext().getBeanDefinitionCount()));
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
        machinePrinter.showTurnFinished(event.duration());
    }

//    @EventListener
    // todo удалить к концу проекта
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

    private List<BootLine> collectBootLines(int beanDefinitionCount) {
        List<BootLine> lines = new ArrayList<>();
        lines.add(BootLine.of(POWER_CELL, BootStatus.ONLINE,
                "спринг контекст " + plural(beanDefinitionCount, "бин", "бина", "бинов"))) ;
        lines.add(BootLine.of(NEUROPROCESSOR,BootStatus.ONLINE, agent.chatModel()));
        lines.add(BootLine.of(MEMORY_BANK, BootStatus.ONLINE, agent.conversation().getClass().getSimpleName()));
        lines.add(BootLine.of(DIAGNOSTIC_BUS, BootStatus.ONLINE,
                "Agent Events: "+AgentEvent.class.getPermittedSubclasses().length));
        return lines;
    }

}
