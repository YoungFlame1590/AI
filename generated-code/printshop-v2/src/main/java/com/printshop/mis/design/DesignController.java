package com.printshop.mis.design;

import com.printshop.common.api.ApiResponse;
import com.printshop.mis.domain.DesignTemplate;
import com.printshop.mis.shared.ApiSupport;
import java.security.Principal;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DesignController {

    private final DesignService designService;
    private final ApiSupport api;

    public DesignController(DesignService designService, ApiSupport api) {
        this.designService = designService;
        this.api = api;
    }

    @GetMapping("/api/design-templates")
    public ApiResponse<?> templates(Principal principal) {
        return api.ok(designService.listTemplates(principal.getName()));
    }

    @PostMapping("/api/design-templates")
    public ApiResponse<?> createTemplate(Principal principal, @RequestBody DesignTemplate request) {
        return api.ok(designService.saveTemplate(principal.getName(), request));
    }

    @PutMapping("/api/design-templates/{id}")
    public ApiResponse<?> updateTemplate(Principal principal, @PathVariable Long id, @RequestBody DesignTemplate request) {
        request.id = id;
        return api.ok(designService.saveTemplate(principal.getName(), request));
    }

    @GetMapping("/api/design-projects")
    public ApiResponse<?> projects(Principal principal) {
        return api.ok(designService.listProjects(principal.getName()));
    }

    @PostMapping("/api/design-projects")
    public ApiResponse<?> createProject(Principal principal, @RequestBody Map<String, Object> request) {
        return api.ok(designService.createProject(principal.getName(), request));
    }

    @GetMapping("/api/design-projects/{id}")
    public ApiResponse<?> project(Principal principal, @PathVariable Long id) {
        return api.ok(designService.getProject(principal.getName(), id));
    }

    @PostMapping("/api/design-projects/{id}/versions")
    public ApiResponse<?> saveVersion(Principal principal, @PathVariable Long id, @RequestBody Map<String, Object> request) {
        return api.ok(designService.saveVersion(principal.getName(), id, request));
    }

    @PostMapping("/api/design-projects/{id}/restore/{versionNo}")
    public ApiResponse<?> restoreVersion(Principal principal, @PathVariable Long id, @PathVariable Integer versionNo) {
        return api.ok(designService.restoreVersion(principal.getName(), id, versionNo));
    }

    @PostMapping("/api/design-projects/{id}/submit-order")
    public ApiResponse<?> submitOrder(Principal principal, @PathVariable Long id, @RequestBody Map<String, Object> request) {
        return api.ok(designService.submitOrder(principal.getName(), id, request));
    }
}
