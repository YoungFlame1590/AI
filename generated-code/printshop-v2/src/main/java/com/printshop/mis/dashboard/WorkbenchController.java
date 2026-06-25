package com.printshop.mis.dashboard;

import com.printshop.common.api.ApiResponse;
import com.printshop.mis.shared.ApiSupport;
import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WorkbenchController {

    private final WorkbenchService workbenchService;
    private final ApiSupport api;

    public WorkbenchController(WorkbenchService workbenchService, ApiSupport api) {
        this.workbenchService = workbenchService;
        this.api = api;
    }

    @GetMapping("/api/workbench/tasks")
    public ApiResponse<?> tasks(Principal principal) {
        return api.ok(workbenchService.tasks(principal.getName()));
    }
}
