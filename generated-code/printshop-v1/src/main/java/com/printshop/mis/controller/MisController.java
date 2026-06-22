package com.printshop.mis.controller;

import com.printshop.common.api.ApiResponse;
import com.printshop.infra.trace.TraceIdProvider;
import com.printshop.mis.domain.DeliveryTask;
import com.printshop.mis.domain.InventoryItem;
import com.printshop.mis.domain.InvoiceRecord;
import com.printshop.mis.domain.JobTicket;
import com.printshop.mis.domain.PaymentRecord;
import com.printshop.mis.domain.PrintOrder;
import com.printshop.mis.domain.ProductionTask;
import com.printshop.mis.domain.Quotation;
import com.printshop.mis.service.MisService;
import com.printshop.mis.service.MisService.LoginRequest;
import java.security.Principal;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class MisController {

    private final MisService service;
    private final TraceIdProvider traceIdProvider;

    public MisController(MisService service, TraceIdProvider traceIdProvider) {
        this.service = service;
        this.traceIdProvider = traceIdProvider;
    }

    @PostMapping("/api/auth/login")
    public ApiResponse<?> login(@RequestBody LoginRequest request) {
        return ok(service.login(request));
    }

    @GetMapping("/api/me")
    public ApiResponse<?> me(Principal principal) {
        return ok(service.me(principal.getName()));
    }

    @GetMapping("/api/me/dashboard")
    public ApiResponse<?> dashboard(Principal principal) {
        return ok(service.dashboard(principal.getName()));
    }

    @GetMapping("/api/stores")
    public ApiResponse<?> stores() {
        return ok(service.stores());
    }

    @GetMapping("/api/users")
    public ApiResponse<?> users() {
        return ok(service.users());
    }

    @GetMapping("/api/orders")
    public ApiResponse<?> orders(Principal principal) {
        return ok(service.listOrders(principal.getName()));
    }

    @PostMapping("/api/orders")
    public ApiResponse<?> createOrder(Principal principal, @RequestBody PrintOrder request) {
        return ok(service.createOrder(principal.getName(), request));
    }

    @GetMapping("/api/orders/{id}")
    public ApiResponse<?> getOrder(@PathVariable Long id) {
        return ok(service.getOrder(id));
    }

    @PutMapping("/api/orders/{id}")
    public ApiResponse<?> updateOrder(Principal principal, @PathVariable Long id, @RequestBody PrintOrder request) {
        return ok(service.updateOrder(principal.getName(), id, request));
    }

    @PostMapping("/api/orders/{id}/status")
    public ApiResponse<?> changeOrderStatus(Principal principal, @PathVariable Long id, @RequestBody Map<String, Object> request) {
        return ok(service.changeOrderStatus(principal.getName(), id, request));
    }

    @DeleteMapping("/api/orders/{id}")
    public ApiResponse<?> deleteOrder(Principal principal, @PathVariable Long id) {
        return ok(service.deleteOrder(principal.getName(), id));
    }

    @PostMapping(path = "/api/orders/{id}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<?> uploadOrderFile(Principal principal, @PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return ok(service.uploadFile(principal.getName(), id, file));
    }

    @GetMapping("/api/orders/{id}/files")
    public ApiResponse<?> orderFiles(@PathVariable Long id) {
        return ok(service.orderFiles(id));
    }

    @GetMapping("/api/quotations")
    public ApiResponse<?> quotations() {
        return ok(service.quotations());
    }

    @PostMapping("/api/quotations")
    public ApiResponse<?> createQuotation(Principal principal, @RequestBody Quotation request) {
        return ok(service.createQuotation(principal.getName(), request));
    }

    @GetMapping("/api/quotations/{id}")
    public ApiResponse<?> getQuotation(@PathVariable Long id) {
        return ok(service.getQuotation(id));
    }

    @PutMapping("/api/quotations/{id}")
    public ApiResponse<?> updateQuotation(Principal principal, @PathVariable Long id, @RequestBody Quotation request) {
        return ok(service.updateQuotation(principal.getName(), id, request));
    }

    @PostMapping("/api/quotations/{id}/approve")
    public ApiResponse<?> approveQuotation(Principal principal, @PathVariable Long id) {
        return ok(service.approveQuotation(principal.getName(), id));
    }

    @DeleteMapping("/api/quotations/{id}")
    public ApiResponse<?> deleteQuotation(Principal principal, @PathVariable Long id) {
        return ok(service.deleteQuotation(principal.getName(), id));
    }

    @GetMapping("/api/job-tickets")
    public ApiResponse<?> jobTickets() {
        return ok(service.jobTickets());
    }

    @PostMapping("/api/job-tickets")
    public ApiResponse<?> createJobTicket(Principal principal, @RequestBody JobTicket request) {
        return ok(service.createJobTicket(principal.getName(), request));
    }

    @GetMapping("/api/job-tickets/{id}")
    public ApiResponse<?> getJobTicket(@PathVariable Long id) {
        return ok(service.getJobTicket(id));
    }

    @PutMapping("/api/job-tickets/{id}")
    public ApiResponse<?> updateJobTicket(Principal principal, @PathVariable Long id, @RequestBody JobTicket request) {
        return ok(service.updateJobTicket(principal.getName(), id, request));
    }

    @DeleteMapping("/api/job-tickets/{id}")
    public ApiResponse<?> deleteJobTicket(Principal principal, @PathVariable Long id) {
        return ok(service.deleteJobTicket(principal.getName(), id));
    }

    @GetMapping("/api/production-tasks")
    public ApiResponse<?> productionTasks() {
        return ok(service.productionTasks());
    }

    @PostMapping("/api/production-tasks")
    public ApiResponse<?> createProductionTask(Principal principal, @RequestBody ProductionTask request) {
        return ok(service.createProductionTask(principal.getName(), request));
    }

    @GetMapping("/api/production-tasks/{id}")
    public ApiResponse<?> getProductionTask(@PathVariable Long id) {
        return ok(service.getProductionTask(id));
    }

    @PutMapping("/api/production-tasks/{id}")
    public ApiResponse<?> updateProductionTask(Principal principal, @PathVariable Long id, @RequestBody ProductionTask request) {
        return ok(service.updateProductionTask(principal.getName(), id, request));
    }

    @PostMapping("/api/production-tasks/{id}/complete")
    public ApiResponse<?> completeProductionTask(Principal principal, @PathVariable Long id) {
        return ok(service.completeProductionTask(principal.getName(), id));
    }

    @DeleteMapping("/api/production-tasks/{id}")
    public ApiResponse<?> deleteProductionTask(Principal principal, @PathVariable Long id) {
        return ok(service.deleteProductionTask(principal.getName(), id));
    }

    @GetMapping("/api/inventory-items")
    public ApiResponse<?> inventoryItems() {
        return ok(service.inventoryItems());
    }

    @PostMapping("/api/inventory-items")
    public ApiResponse<?> saveInventory(Principal principal, @RequestBody InventoryItem request) {
        return ok(service.saveInventory(principal.getName(), request));
    }

    @GetMapping("/api/inventory-items/{id}")
    public ApiResponse<?> getInventoryItem(@PathVariable Long id) {
        return ok(service.getInventoryItem(id));
    }

    @PutMapping("/api/inventory-items/{id}")
    public ApiResponse<?> updateInventory(Principal principal, @PathVariable Long id, @RequestBody InventoryItem request) {
        request.id = id;
        return ok(service.saveInventory(principal.getName(), request));
    }

    @PostMapping("/api/inventory-items/{id}/adjust")
    public ApiResponse<?> adjustInventory(Principal principal, @PathVariable Long id, @RequestBody Map<String, Object> request) {
        return ok(service.adjustInventory(principal.getName(), id, request));
    }

    @DeleteMapping("/api/inventory-items/{id}")
    public ApiResponse<?> deleteInventory(Principal principal, @PathVariable Long id) {
        return ok(service.deleteInventory(principal.getName(), id));
    }

    @GetMapping("/api/delivery-tasks")
    public ApiResponse<?> deliveryTasks() {
        return ok(service.deliveryTasks());
    }

    @PostMapping("/api/delivery-tasks")
    public ApiResponse<?> createDeliveryTask(Principal principal, @RequestBody DeliveryTask request) {
        return ok(service.createDeliveryTask(principal.getName(), request));
    }

    @GetMapping("/api/delivery-tasks/{id}")
    public ApiResponse<?> getDeliveryTask(@PathVariable Long id) {
        return ok(service.getDeliveryTask(id));
    }

    @PutMapping("/api/delivery-tasks/{id}")
    public ApiResponse<?> updateDeliveryTask(Principal principal, @PathVariable Long id, @RequestBody DeliveryTask request) {
        return ok(service.updateDeliveryTask(principal.getName(), id, request));
    }

    @PostMapping("/api/delivery-tasks/{id}/sign")
    public ApiResponse<?> signDelivery(Principal principal, @PathVariable Long id, @RequestBody Map<String, Object> request) {
        return ok(service.signDelivery(principal.getName(), id, request));
    }

    @DeleteMapping("/api/delivery-tasks/{id}")
    public ApiResponse<?> deleteDeliveryTask(Principal principal, @PathVariable Long id) {
        return ok(service.deleteDeliveryTask(principal.getName(), id));
    }

    @GetMapping("/api/invoices")
    public ApiResponse<?> invoices() {
        return ok(service.invoices());
    }

    @PostMapping("/api/invoices")
    public ApiResponse<?> createInvoice(Principal principal, @RequestBody InvoiceRecord request) {
        return ok(service.createInvoice(principal.getName(), request));
    }

    @GetMapping("/api/invoices/{id}")
    public ApiResponse<?> getInvoice(@PathVariable Long id) {
        return ok(service.getInvoice(id));
    }

    @PutMapping("/api/invoices/{id}")
    public ApiResponse<?> updateInvoice(Principal principal, @PathVariable Long id, @RequestBody InvoiceRecord request) {
        return ok(service.updateInvoice(principal.getName(), id, request));
    }

    @PostMapping("/api/invoices/{id}/issue")
    public ApiResponse<?> issueInvoice(Principal principal, @PathVariable Long id) {
        return ok(service.issueInvoice(principal.getName(), id));
    }

    @DeleteMapping("/api/invoices/{id}")
    public ApiResponse<?> deleteInvoice(Principal principal, @PathVariable Long id) {
        return ok(service.deleteInvoice(principal.getName(), id));
    }

    @GetMapping("/api/payments")
    public ApiResponse<?> payments() {
        return ok(service.payments());
    }

    @PostMapping("/api/payments")
    public ApiResponse<?> createPayment(Principal principal, @RequestBody PaymentRecord request) {
        return ok(service.createPayment(principal.getName(), request));
    }

    @GetMapping("/api/payments/{id}")
    public ApiResponse<?> getPayment(@PathVariable Long id) {
        return ok(service.getPayment(id));
    }

    @PutMapping("/api/payments/{id}")
    public ApiResponse<?> updatePayment(Principal principal, @PathVariable Long id, @RequestBody PaymentRecord request) {
        return ok(service.updatePayment(principal.getName(), id, request));
    }

    @PostMapping("/api/payments/{id}/refund")
    public ApiResponse<?> refundPayment(Principal principal, @PathVariable Long id) {
        return ok(service.refundPayment(principal.getName(), id));
    }

    @DeleteMapping("/api/payments/{id}")
    public ApiResponse<?> deletePayment(Principal principal, @PathVariable Long id) {
        return ok(service.deletePayment(principal.getName(), id));
    }

    @GetMapping("/api/audit-logs")
    public ApiResponse<?> auditLogs() {
        return ok(service.auditLogs());
    }

    @GetMapping("/api/audit-logs/{id}")
    public ApiResponse<?> auditLog(@PathVariable Long id) {
        return ok(service.getAuditLog(id));
    }

    @GetMapping("/api/reports")
    public ApiResponse<?> reports() {
        return ok(service.reports());
    }

    private ApiResponse<?> ok(Object data) {
        return ApiResponse.ok(data, traceIdProvider.currentTraceId());
    }
}
