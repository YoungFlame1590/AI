package com.printshop.mis.order;

import com.printshop.common.api.ApiResponse;
import com.printshop.mis.domain.PrintOrder;
import com.printshop.mis.shared.ApiSupport;
import java.security.Principal;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class OrderController {

    private final OrderService orderService;
    private final ApiSupport api;

    public OrderController(OrderService orderService, ApiSupport api) {
        this.orderService = orderService;
        this.api = api;
    }

    @GetMapping("/api/orders")
    public ApiResponse<?> orders(Principal principal) {
        return api.ok(orderService.listOrders(principal.getName()));
    }

    @PostMapping("/api/orders")
    public ApiResponse<?> createOrder(Principal principal, @RequestBody PrintOrder request) {
        return api.ok(orderService.createOrder(principal.getName(), request));
    }

    @GetMapping("/api/orders/{id}")
    public ApiResponse<?> getOrder(Principal principal, @PathVariable Long id) {
        return api.ok(orderService.getOrder(principal.getName(), id));
    }

    @PutMapping("/api/orders/{id}")
    public ApiResponse<?> updateOrder(Principal principal, @PathVariable Long id, @RequestBody PrintOrder request) {
        return api.ok(orderService.updateOrder(principal.getName(), id, request));
    }

    @PostMapping("/api/orders/{id}/status")
    public ApiResponse<?> changeOrderStatus(Principal principal, @PathVariable Long id, @RequestBody Map<String, Object> request) {
        return api.ok(orderService.changeOrderStatus(principal.getName(), id, request));
    }

    @DeleteMapping("/api/orders/{id}")
    public ApiResponse<?> deleteOrder(Principal principal, @PathVariable Long id) {
        return api.ok(orderService.deleteOrder(principal.getName(), id));
    }

    @PostMapping(path = "/api/orders/{id}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<?> uploadOrderFile(Principal principal, @PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return api.ok(orderService.uploadFile(principal.getName(), id, file));
    }

    @GetMapping("/api/orders/{id}/files")
    public ApiResponse<?> orderFiles(Principal principal, @PathVariable Long id) {
        return api.ok(orderService.orderFiles(principal.getName(), id));
    }
}
