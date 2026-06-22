package com.printshop.workbench.dto;

import com.printshop.aud.dto.AuditLog;
import com.printshop.common.api.StatsResponse;
import java.util.List;

/**
 * 前端角色工作台快照。
 */
public record WorkbenchSnapshot(
        RoleProfile role,
        List<WorkbenchMetric> metrics,
        List<WorkbenchTask> tasks,
        List<WorkbenchOrder> orders,
        List<RoleAction> actions,
        List<AuditLog> audits,
        StatsResponse stats,
        String message
) {
}
