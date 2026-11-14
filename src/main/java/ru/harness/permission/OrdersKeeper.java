package ru.harness.permission;

import ru.harness.tool.WorkspaceGuard;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Component
public class OrdersKeeper {
    private final OrdersCollector collector;
    private final WorkspaceGuard guard;
    private ToolPermissionContext current;

    public OrdersKeeper(OrdersCollector collector, WorkspaceGuard guard) {
        this.collector = collector;
        this.guard = guard;
        this.current = collector.load();
    }

    public synchronized ToolPermissionContext snapshot() {
        return current;
    }

    public synchronized void apply(PermissionUpdate update) {
        switch (update) {
            case PermissionUpdate.Directory added -> {
                var directories = new ArrayList<>(current.additionalWorkingDirectories());
                directories.add(guard.resolve(added.path()));
                current = new ToolPermissionContext(current.mode(), current.ruleEntries(), directories);
            }
            case PermissionUpdate.Rule added -> {
                if (added.destination() != PermissionSource.SESSION && added.destination() != PermissionSource.LOCAL) {
                    throw new IllegalArgumentException("на диск сохраняем только правило в local");
                }
                var entry = new PermissionEntry(added.rule(), added.behavior(), added.destination());
                // Сначала файл: сбой записи не должен опубликовать постоянное согласие.
                if (added.destination() == PermissionSource.LOCAL) collector.saveLocalRule(entry);
                if (current.ruleEntries().contains(entry)) return;
                var rules = new ArrayList<>(current.ruleEntries());
                rules.add(entry);
                current = new ToolPermissionContext(current.mode(), rules, current.additionalWorkingDirectories());
            }
        }
    }
}
