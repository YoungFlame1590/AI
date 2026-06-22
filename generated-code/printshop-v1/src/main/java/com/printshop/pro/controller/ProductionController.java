package com.printshop.pro.controller;

import com.printshop.common.api.ApiResponse;
import com.printshop.infra.stats.StatsRecorder;
import com.printshop.infra.trace.TraceIdProvider;
import com.printshop.pro.application.ProductionAppService;
import com.printshop.pro.dto.ProductionTask;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PRO Controller，仅负责 HTTP 契约适配。
 */
@RestController
@RequestMapping("/api/v1/productions")
public class ProductionController {

    private final ProductionAppService productionAppService;
    private final StatsRecorder statsRecorder;
    private final TraceIdProvider traceIdProvider;

    public ProductionController(ProductionAppService productionAppService, StatsRecorder statsRecorder, TraceIdProvider traceIdProvider) {
        this.productionAppService = productionAppService;
        this.statsRecorder = statsRecorder;
        this.traceIdProvider = traceIdProvider;
    }

    @Operation(operationId = "dispatchProduction", summary = "下发排产指令与额度校验")
    @PostMapping("/dispatch")
    public ApiResponse<ProductionTask> dispatchProduction(@RequestBody ProductionTask request) {
        statsRecorder.record("PRO");
        return ApiResponse.ok(productionAppService.dispatchProduction(request), traceIdProvider.currentTraceId());
    }
}
