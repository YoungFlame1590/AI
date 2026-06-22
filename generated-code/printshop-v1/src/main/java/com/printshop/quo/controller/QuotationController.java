package com.printshop.quo.controller;

import com.printshop.common.api.ApiResponse;
import com.printshop.infra.stats.StatsRecorder;
import com.printshop.infra.trace.TraceIdProvider;
import com.printshop.quo.application.QuotationAppService;
import com.printshop.quo.dto.Quotation;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * QUO Controller，仅负责 HTTP 契约适配。
 */
@RestController
@RequestMapping("/api/v1/quotations")
public class QuotationController {

    private final QuotationAppService quotationAppService;
    private final StatsRecorder statsRecorder;
    private final TraceIdProvider traceIdProvider;

    public QuotationController(QuotationAppService quotationAppService, StatsRecorder statsRecorder, TraceIdProvider traceIdProvider) {
        this.quotationAppService = quotationAppService;
        this.statsRecorder = statsRecorder;
        this.traceIdProvider = traceIdProvider;
    }

    @Operation(operationId = "calculateQuotation", summary = "计算阶梯报价与折扣校验")
    @PostMapping("/calculate")
    public ApiResponse<Quotation> calculateQuotation(@RequestBody Quotation request) {
        statsRecorder.record("QUO");
        return ApiResponse.ok(quotationAppService.calculateQuotation(request), traceIdProvider.currentTraceId());
    }
}
