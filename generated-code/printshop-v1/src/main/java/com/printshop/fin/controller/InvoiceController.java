package com.printshop.fin.controller;

import com.printshop.common.api.ApiResponse;
import com.printshop.fin.application.InvoiceAppService;
import com.printshop.fin.dto.Invoice;
import com.printshop.infra.stats.StatsRecorder;
import com.printshop.infra.trace.TraceIdProvider;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * FIN Controller，仅负责 HTTP 契约适配。
 */
@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceController {

    private final InvoiceAppService invoiceAppService;
    private final StatsRecorder statsRecorder;
    private final TraceIdProvider traceIdProvider;

    public InvoiceController(InvoiceAppService invoiceAppService, StatsRecorder statsRecorder, TraceIdProvider traceIdProvider) {
        this.invoiceAppService = invoiceAppService;
        this.statsRecorder = statsRecorder;
        this.traceIdProvider = traceIdProvider;
    }

    @Operation(operationId = "issueInvoice", summary = "开具电子发票与外协占比拦截")
    @PostMapping("/issue")
    public ApiResponse<Invoice> issueInvoice(@RequestBody Invoice request) {
        statsRecorder.record("FIN");
        return ApiResponse.ok(invoiceAppService.issueInvoice(request), traceIdProvider.currentTraceId());
    }
}
