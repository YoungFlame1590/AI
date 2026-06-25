package com.printshop.mis.shared;

import com.printshop.common.api.ApiResponse;
import com.printshop.infra.trace.TraceIdProvider;
import org.springframework.stereotype.Component;

@Component
public class ApiSupport {

    private final TraceIdProvider traceIdProvider;

    public ApiSupport(TraceIdProvider traceIdProvider) {
        this.traceIdProvider = traceIdProvider;
    }

    public ApiResponse<?> ok(Object data) {
        return ApiResponse.ok(data, traceIdProvider.currentTraceId());
    }
}
