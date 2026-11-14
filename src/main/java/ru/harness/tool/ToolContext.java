package ru.harness.tool;

import org.springframework.stereotype.Component;
import ru.harness.permission.OrdersKeeper;
import ru.harness.permission.ToolPermissionContext;

@Component
public record ToolContext(FileStateCache readFileState, OrdersKeeper ordersKeeper) {
    public ToolPermissionContext permissions() {
        return ordersKeeper.snapshot();
    }
}
