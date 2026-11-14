package ru.harness.permission;

import ru.harness.tool.Tool;
import ru.harness.tool.ToolContext;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PermissionEvaluator {
    private final OrdersKeeper ordersKeeper;

    public PermissionEvaluator(OrdersKeeper ordersKeeper) {
        this.ordersKeeper = ordersKeeper;
    }

    /** Правило на весь инструмент скрывает его из списка модели; запрет отдельного пути — нет. */
    public boolean isAvailable(Tool<?> tool, ToolContext context) {
        return context.permissions()
                .wholeToolRule(tool.name(), PermissionBehavior.DENY) == null;
    }

    public void remember(List<PermissionUpdate> suggestions) {
        suggestions.forEach(ordersKeeper::apply);
    }

    public PermissionMode mode() {
        // HUD получает текущий режим без запуска проверки конкретного вызова.
        return ordersKeeper.snapshot().mode();
    }

    /** Только подсказка для стартового экрана. Конкретный вызов проверит evaluate. */
    public boolean isLocked(Tool<?> tool) {
        // Только значок защиты для HUD: это приблизительная подсказка, а не разрешение на выполнение.
        PermissionMode mode = mode();
        return !tool.isReadOnly() && mode != PermissionMode.BYPASS_PERMISSIONS && !(mode == PermissionMode.ACCEPT_EDITS && (tool.name().equals("Edit") || tool.name().equals("Write")));
    }

    public <I> PermissionDecision evaluate(Tool<I> tool, I input, ToolContext context) {
        ToolPermissionContext settings = context.permissions();
        PermissionEntry deny = settings.wholeToolRule(tool.name(), PermissionBehavior.DENY);
        if (deny != null) return PermissionDecision.deny("запрещено правилом " + deny.rule());

        PermissionDecision fromTool = tool.checkPermissions(input, context);
        if (fromTool.behavior() == PermissionBehavior.DENY) return fromTool;

        PermissionEntry ask = settings.wholeToolRule(tool.name(), PermissionBehavior.ASK);
        if (ask != null) return PermissionDecision.askAlways("вопрос по правилу " + ask.rule());

        if(fromTool.askAlways()) return fromTool;

        if (settings.mode() == PermissionMode.BYPASS_PERMISSIONS) return PermissionDecision.allow("режим bypassPermissions");

        PermissionEntry allow = settings.wholeToolRule(tool.name(), PermissionBehavior.ALLOW);
        if (allow != null) return PermissionDecision.allow("разрешено правилом " + allow.rule());

        return fromTool;



    }
}
