package com.printshop.common.api;

import java.time.Instant;
import java.util.Map;

/**
 * /stats 统计响应。
 *
 * @param totalRequests 已记录接口调用总数
 * @param moduleCounts 各业务模块调用次数
 * @param lastRequestAt 最后一次调用时间
 */
public record StatsResponse(long totalRequests, Map<String, Long> moduleCounts, Instant lastRequestAt) {
}
