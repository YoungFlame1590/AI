package com.printshop.mis.inventory;

import com.printshop.common.api.ApiResponse;
import com.printshop.mis.domain.InventoryItem;
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
public class InventoryController {

    private final InventoryService inventoryService;
    private final ApiSupport api;

    public InventoryController(InventoryService inventoryService, ApiSupport api) {
        this.inventoryService = inventoryService;
        this.api = api;
    }

    @GetMapping("/api/inventory-items")
    public ApiResponse<?> inventoryItems() {
        return api.ok(inventoryService.inventoryItems());
    }

    @PostMapping("/api/inventory-items")
    public ApiResponse<?> saveInventory(Principal principal, @RequestBody InventoryItem request) {
        return api.ok(inventoryService.saveInventory(principal.getName(), request));
    }

    @GetMapping("/api/inventory-items/{id}")
    public ApiResponse<?> getInventoryItem(@PathVariable Long id) {
        return api.ok(inventoryService.getInventoryItem(id));
    }

    @PutMapping("/api/inventory-items/{id}")
    public ApiResponse<?> updateInventory(Principal principal, @PathVariable Long id, @RequestBody InventoryItem request) {
        request.id = id;
        return api.ok(inventoryService.saveInventory(principal.getName(), request));
    }

    @PostMapping("/api/inventory-items/{id}/adjust")
    public ApiResponse<?> adjustInventory(Principal principal, @PathVariable Long id, @RequestBody Map<String, Object> request) {
        return api.ok(inventoryService.adjustInventory(principal.getName(), id, request));
    }

    @DeleteMapping("/api/inventory-items/{id}")
    public ApiResponse<?> deleteInventory(Principal principal, @PathVariable Long id) {
        return api.ok(inventoryService.deleteInventory(principal.getName(), id));
    }
}
