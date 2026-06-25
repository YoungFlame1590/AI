package com.printshop.mis.delivery;

import com.printshop.common.api.ApiResponse;
import com.printshop.mis.domain.DeliveryTask;
import com.printshop.mis.shared.ApiSupport;
import java.security.Principal;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DeliveryController {

    private final DeliveryService deliveryService;
    private final ApiSupport api;

    public DeliveryController(DeliveryService deliveryService, ApiSupport api) {
        this.deliveryService = deliveryService;
        this.api = api;
    }

    @GetMapping("/api/delivery-tasks")
    public ApiResponse<?> deliveryTasks(Principal principal) {
        return api.ok(deliveryService.deliveryTasks(principal.getName()));
    }

    @PostMapping("/api/delivery-tasks")
    public ApiResponse<?> createDeliveryTask(Principal principal, @RequestBody DeliveryTask request) {
        return api.ok(deliveryService.createDeliveryTask(principal.getName(), request));
    }

    @GetMapping("/api/delivery-tasks/{id}")
    public ApiResponse<?> getDeliveryTask(@PathVariable Long id) {
        return api.ok(deliveryService.getDeliveryTask(id));
    }

    @PutMapping("/api/delivery-tasks/{id}")
    public ApiResponse<?> updateDeliveryTask(Principal principal, @PathVariable Long id, @RequestBody DeliveryTask request) {
        return api.ok(deliveryService.updateDeliveryTask(principal.getName(), id, request));
    }

    @PostMapping("/api/delivery-tasks/{id}/sign")
    public ApiResponse<?> signDelivery(Principal principal, @PathVariable Long id, @RequestBody Map<String, Object> request) {
        return api.ok(deliveryService.signDelivery(principal.getName(), id, request));
    }

    @DeleteMapping("/api/delivery-tasks/{id}")
    public ApiResponse<?> deleteDeliveryTask(Principal principal, @PathVariable Long id) {
        return api.ok(deliveryService.deleteDeliveryTask(principal.getName(), id));
    }
}
