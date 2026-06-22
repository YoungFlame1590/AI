package com.printshop.workbench.dto;

/**
 * v1 演示用户角色。
 */
public record RoleProfile(
        String roleId,
        String roleName,
        String userId,
        String userName,
        String moduleCode,
        String focus
) {
}
