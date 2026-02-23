package com.iptnet.consume.service.dashboard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DashboardWarmupRunner implements ApplicationRunner {

    private final DashboardCacheService dashboardCacheService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            dashboardCacheService.warmupRequiredData();
        } catch (Exception e) {
            log.error("dashboard cache warmup failed", e);
        }
    }
}
