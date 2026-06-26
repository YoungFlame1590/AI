package com.printshop.mis.production;

import com.printshop.common.api.ApiResponse;
import com.printshop.mis.domain.ProductionTask;
import com.printshop.mis.shared.ApiSupport;
import java.security.Principal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProductionTaskController {

    private final ProductionTaskService productionTaskService;
    private final ApiSupport api;

    public ProductionTaskController(ProductionTaskService productionTaskService, ApiSupport api) {
        this.productionTaskService = productionTaskService;
        this.api = api;
    }

    @GetMapping("/api/production-tasks")
    public ApiResponse<?> productionTasks(Principal principal) {
        return api.ok(productionTaskService.productionTasks(principal.getName()));
    }

    @PostMapping("/api/production-tasks")
    public ApiResponse<?> createProductionTask(Principal principal, @RequestBody ProductionTask request) {
        return api.ok(productionTaskService.createProductionTask(principal.getName(), request));
    }

    @GetMapping("/api/production-tasks/{id}")
    public ApiResponse<?> getProductionTask(Principal principal, @PathVariable Long id) {
        return api.ok(productionTaskService.getProductionTask(principal.getName(), id));
    }

    @PutMapping("/api/production-tasks/{id}")
    public ApiResponse<?> updateProductionTask(Principal principal, @PathVariable Long id, @RequestBody ProductionTask request) {
        return api.ok(productionTaskService.updateProductionTask(principal.getName(), id, request));
    }

    @PostMapping("/api/production-tasks/{id}/complete")
    public ApiResponse<?> completeProductionTask(Principal principal, @PathVariable Long id) {
        return api.ok(productionTaskService.completeProductionTask(principal.getName(), id));
    }

    @DeleteMapping("/api/production-tasks/{id}")
    public ApiResponse<?> deleteProductionTask(Principal principal, @PathVariable Long id) {
        return api.ok(productionTaskService.deleteProductionTask(principal.getName(), id));
    }
}
