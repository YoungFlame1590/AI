package com.printshop.ord.controller;

import com.printshop.common.api.ApiResponse;
import com.printshop.infra.stats.StatsRecorder;
import com.printshop.infra.trace.TraceIdProvider;
import com.printshop.ord.application.OrderAppService;
import com.printshop.ord.dto.Order;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ORD Controller，仅负责 HTTP 契约适配。
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderAppService orderAppService;
    private final StatsRecorder statsRecorder;
    private final TraceIdProvider traceIdProvider;

    public OrderController(OrderAppService orderAppService, StatsRecorder statsRecorder, TraceIdProvider traceIdProvider) {
        this.orderAppService = orderAppService;
        this.statsRecorder = statsRecorder;
        this.traceIdProvider = traceIdProvider;
    }

    @Operation(operationId = "createOrder", summary = "创建订单与文件校验")
    @PostMapping
    public ApiResponse<Order> createOrder(@RequestBody Order request) {
        statsRecorder.record("ORD");
        return ApiResponse.ok(orderAppService.createOrder(request), traceIdProvider.currentTraceId());
    }
}
