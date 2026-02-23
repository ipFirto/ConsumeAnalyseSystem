package com.iptnet.consume.service.dashboard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardAggregateService {

    private final DashboardCacheService dashboardCacheService;
    private final DashboardEventPublisher dashboardEventPublisher;

    public void rebuildAndPublish(Integer userId, String reason) {
        dashboardCacheService.refreshHomeCharts();
        if (userId != null && userId > 0) {
            dashboardCacheService.refreshRecentOrders(userId);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reason", reason == null || reason.isBlank() ? "UNKNOWN" : reason);
        payload.put("ts", Instant.now().toString());
        payload.put("platformIds", dashboardCacheService.getPlatformIds());
        payload.put("bar", dashboardCacheService.getBarCounts());
        payload.put("smooth", dashboardCacheService.getSmoothRows());
        payload.put("lineByPlatform", dashboardCacheService.getAllLineRows());
        if (userId != null && userId > 0) {
            payload.put("userId", userId);
            payload.put("recentOrders", dashboardCacheService.getRecentOrders(userId));
        }

        dashboardEventPublisher.publishPatch("home", "merge", payload);
        log.debug("dashboard aggregated and pushed, userId={}, reason={}", userId, reason);
    }
}
