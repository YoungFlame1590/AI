package com.printshop.infra.trace;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 链路追踪标识提供器。
 */
@Component
public class TraceIdProvider {

    private final HttpServletRequest request;

    public TraceIdProvider(HttpServletRequest request) {
        this.request = request;
    }

    public String currentTraceId() {
        String header = request.getHeader("X-Trace-Id");
        return header == null || header.isBlank() ? UUID.randomUUID().toString() : header;
    }
}
