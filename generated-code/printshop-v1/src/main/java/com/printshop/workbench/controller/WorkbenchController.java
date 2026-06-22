package com.printshop.workbench.controller;

import com.printshop.workbench.application.WorkbenchService;
import com.printshop.workbench.dto.RoleProfile;
import com.printshop.workbench.dto.WorkbenchActionRequest;
import com.printshop.workbench.dto.WorkbenchSnapshot;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 七类涉众角色工作台 API。
 */
@RestController
@RequestMapping("/api/v1")
public class WorkbenchController {

    private final WorkbenchService workbenchService;

    public WorkbenchController(WorkbenchService workbenchService) {
        this.workbenchService = workbenchService;
    }

    @Operation(operationId = "getRoles", summary = "查询 v1 演示角色")
    @GetMapping("/roles")
    public List<RoleProfile> roles() {
        return workbenchService.roles();
    }

    @Operation(operationId = "getWorkbench", summary = "查询角色工作台快照")
    @GetMapping("/workbench/{roleId}")
    public WorkbenchSnapshot workbench(@PathVariable String roleId) {
        return workbenchService.snapshot(roleId);
    }

    @Operation(operationId = "runWorkbenchAction", summary = "执行角色工作台动作")
    @PostMapping("/workbench/actions")
    public WorkbenchSnapshot runAction(@RequestBody WorkbenchActionRequest request) {
        return workbenchService.runAction(request);
    }

    @Operation(operationId = "resetWorkbench", summary = "重置 v1 演示数据")
    @PostMapping("/workbench/reset")
    public WorkbenchSnapshot reset(@RequestParam(defaultValue = "customer") String roleId) {
        return workbenchService.reset(roleId);
    }
}
