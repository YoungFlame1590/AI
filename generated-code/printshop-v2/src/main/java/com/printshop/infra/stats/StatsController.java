package com.printshop.infra.stats;

import com.printshop.common.api.StatsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * v1 运行统计接口。
 */
@RestController
public class StatsController {

    private final StatsRecorder statsRecorder;

    public StatsController(StatsRecorder statsRecorder) {
        this.statsRecorder = statsRecorder;
    }

    @GetMapping("/stats")
    public StatsResponse stats() {
        return statsRecorder.snapshot();
    }
}
