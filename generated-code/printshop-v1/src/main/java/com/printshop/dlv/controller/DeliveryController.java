package com.printshop.dlv.controller;

import com.printshop.common.api.ApiResponse;
import com.printshop.dlv.application.DeliveryAppService;
import com.printshop.dlv.dto.DeliveryTask;
import com.printshop.infra.stats.StatsRecorder;
import com.printshop.infra.trace.TraceIdProvider;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DLV Controller，仅负责 HTTP 契约适配。
 */
@RestController
@RequestMapping("/api/v1/deliveries")
public class DeliveryController {

    private final DeliveryAppService deliveryAppService;
    private final StatsRecorder statsRecorder;
    private final TraceIdProvider traceIdProvider;

    public DeliveryController(DeliveryAppService deliveryAppService, StatsRecorder statsRecorder, TraceIdProvider traceIdProvider) {
        this.deliveryAppService = deliveryAppService;
        this.statsRecorder = statsRecorder;
        this.traceIdProvider = traceIdProvider;
    }

    @Operation(operationId = "routeDelivery", summary = "跨店路由决策与任务下发")
    @PostMapping("/route")
    public ApiResponse<DeliveryTask> routeDelivery(@RequestBody DeliveryTask request) {
        statsRecorder.record("DLV");
        return ApiResponse.ok(deliveryAppService.routeDelivery(request), traceIdProvider.currentTraceId());
    }
}
