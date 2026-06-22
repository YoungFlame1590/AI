package com.printshop.workbench.dto;

import java.util.Map;

/**
 * 统一角色动作请求。
 */
public record WorkbenchActionRequest(
        String roleId,
        String action,
        String orderId,
        Map<String, Object> payload
) {
}
