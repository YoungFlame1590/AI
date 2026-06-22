package com.printshop.workbench.dto;

/**
 * 当前角色可执行动作。
 */
public record RoleAction(String actionId, String label, String moduleCode, boolean orderRequired) {
}
