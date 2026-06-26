package com.printshop.mis.finance;

import com.printshop.common.api.ApiResponse;
import com.printshop.mis.domain.InvoiceRecord;
import com.printshop.mis.domain.PaymentRecord;
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
public class FinanceController {

    private final FinanceService financeService;
    private final ApiSupport api;

    public FinanceController(FinanceService financeService, ApiSupport api) {
        this.financeService = financeService;
        this.api = api;
    }

    @GetMapping("/api/invoices")
    public ApiResponse<?> invoices(Principal principal) {
        return api.ok(financeService.invoices(principal.getName()));
    }

    @PostMapping("/api/invoices")
    public ApiResponse<?> createInvoice(Principal principal, @RequestBody InvoiceRecord request) {
        return api.ok(financeService.createInvoice(principal.getName(), request));
    }

    @GetMapping("/api/invoices/{id}")
    public ApiResponse<?> getInvoice(Principal principal, @PathVariable Long id) {
        return api.ok(financeService.getInvoice(principal.getName(), id));
    }

    @PutMapping("/api/invoices/{id}")
    public ApiResponse<?> updateInvoice(Principal principal, @PathVariable Long id, @RequestBody InvoiceRecord request) {
        return api.ok(financeService.updateInvoice(principal.getName(), id, request));
    }

    @PostMapping("/api/invoices/{id}/issue")
    public ApiResponse<?> issueInvoice(Principal principal, @PathVariable Long id) {
        return api.ok(financeService.issueInvoice(principal.getName(), id));
    }

    @DeleteMapping("/api/invoices/{id}")
    public ApiResponse<?> deleteInvoice(Principal principal, @PathVariable Long id) {
        return api.ok(financeService.deleteInvoice(principal.getName(), id));
    }

    @GetMapping("/api/payments")
    public ApiResponse<?> payments(Principal principal) {
        return api.ok(financeService.payments(principal.getName()));
    }

    @PostMapping("/api/payments")
    public ApiResponse<?> createPayment(Principal principal, @RequestBody PaymentRecord request) {
        return api.ok(financeService.createPayment(principal.getName(), request));
    }

    @GetMapping("/api/payments/{id}")
    public ApiResponse<?> getPayment(Principal principal, @PathVariable Long id) {
        return api.ok(financeService.getPayment(principal.getName(), id));
    }

    @PutMapping("/api/payments/{id}")
    public ApiResponse<?> updatePayment(Principal principal, @PathVariable Long id, @RequestBody PaymentRecord request) {
        return api.ok(financeService.updatePayment(principal.getName(), id, request));
    }

    @PostMapping("/api/payments/{id}/refund")
    public ApiResponse<?> refundPayment(Principal principal, @PathVariable Long id) {
        return api.ok(financeService.refundPayment(principal.getName(), id));
    }

    @DeleteMapping("/api/payments/{id}")
    public ApiResponse<?> deletePayment(Principal principal, @PathVariable Long id) {
        return api.ok(financeService.deletePayment(principal.getName(), id));
    }
}
