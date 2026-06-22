package com.printshop.workbench.dto;

/**
 * 角色待办任务。
 */
public record WorkbenchTask(
        String taskId,
        String orderId,
        String title,
        String severity,
        String dueText,
        String actionId
) {
}
