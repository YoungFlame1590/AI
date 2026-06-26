package com.printshop.mis.quotation;

import com.printshop.common.api.ApiResponse;
import com.printshop.mis.domain.Quotation;
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
public class QuotationController {

    private final QuotationService quotationService;
    private final ApiSupport api;

    public QuotationController(QuotationService quotationService, ApiSupport api) {
        this.quotationService = quotationService;
        this.api = api;
    }

    @GetMapping("/api/quotations")
    public ApiResponse<?> quotations(Principal principal) {
        return api.ok(quotationService.quotations(principal.getName()));
    }

    @PostMapping("/api/quotations")
    public ApiResponse<?> createQuotation(Principal principal, @RequestBody Quotation request) {
        return api.ok(quotationService.createQuotation(principal.getName(), request));
    }

    @GetMapping("/api/quotations/{id}")
    public ApiResponse<?> getQuotation(Principal principal, @PathVariable Long id) {
        return api.ok(quotationService.getQuotation(principal.getName(), id));
    }

    @PutMapping("/api/quotations/{id}")
    public ApiResponse<?> updateQuotation(Principal principal, @PathVariable Long id, @RequestBody Quotation request) {
        return api.ok(quotationService.updateQuotation(principal.getName(), id, request));
    }

    @PostMapping("/api/quotations/{id}/approve")
    public ApiResponse<?> approveQuotation(Principal principal, @PathVariable Long id) {
        return api.ok(quotationService.approveQuotation(principal.getName(), id));
    }

    @PostMapping("/api/quotations/{id}/confirm")
    public ApiResponse<?> confirmQuotation(Principal principal, @PathVariable Long id) {
        return api.ok(quotationService.confirmQuotation(principal.getName(), id));
    }

    @DeleteMapping("/api/quotations/{id}")
    public ApiResponse<?> deleteQuotation(Principal principal, @PathVariable Long id) {
        return api.ok(quotationService.deleteQuotation(principal.getName(), id));
    }
}
