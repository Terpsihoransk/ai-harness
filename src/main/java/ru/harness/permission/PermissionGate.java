package ru.harness.permission;

import ru.harness.agent.event.AgentEvent;
import ru.harness.tool.Tool;
import ru.harness.tool.ToolContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class PermissionGate {

    private final PermissionEvaluator permissionEvaluator;
    private final PermissionPrompt permissionPrompt;
    private final ApplicationEventPublisher eventPublisher;

    public PermissionGate(PermissionEvaluator permissionEvaluator, PermissionPrompt permissionPrompt, ApplicationEventPublisher eventPublisher) {
        this.permissionEvaluator = permissionEvaluator;
        this.permissionPrompt = permissionPrompt;
        this.eventPublisher = eventPublisher;
    }

    public <I> PermissionDecision authorize(Tool<I> tool, I input, ToolContext context) {
        PermissionDecision decision = permissionEvaluator.evaluate(tool, input, context);
        if (decision.behavior() != PermissionBehavior.ASK) return decision;

        String target = tool.renderToolUseMessage(input);
        eventPublisher.publishEvent(new AgentEvent.PermissionRequested(tool.name(), target, decision.reason()));
        PermissionPrompt.Answer answer = permissionPrompt.ask(tool.name(), target, decision);
        boolean granted = answer != PermissionPrompt.Answer.DENY;
        if(answer == PermissionPrompt.Answer.ALLOW_ALWAYS){
            permissionEvaluator.remember(decision.suggestions());
        }
        eventPublisher.publishEvent(new AgentEvent.PermissionResolved(tool.name(), granted));
        // Сам Tool.call выполнит реестр, не Gate.
        return granted ? PermissionDecision.allow("пользователь разрешил вызов") : PermissionDecision.deny("пользователь отказал в выполнении. Предложи другой способ");
    }

    /** Реестр не предлагает модели Tool, который целиком запрещён. Запрет отдельного аргумента Tool не скрывает. */
    public boolean isAvailable(Tool<?> tool, ToolContext context) {
        return permissionEvaluator.isAvailable(tool, context);
    }

}














