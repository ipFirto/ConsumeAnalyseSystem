package com.iptnet.consume.service.dashboard;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardQueryService {

    private final DashboardCacheService dashboardCacheService;

    public Object snapshot(String scope,
                           Integer platformId,
                           String categoryName,
                           Integer provinceId,
                           Integer userId) {
        String safeScope = scope == null || scope.isBlank() ? "home" : scope.trim().toLowerCase();

        return switch (safeScope) {
            case "home" -> dashboardCacheService.buildHomeSnapshot(platformId, userId);
            case "platform" -> {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("scope", "platform");
                payload.put("platformId", platformId);
                payload.put("line", dashboardCacheService.getLineRows(platformId));
                yield payload;
            }
            case "province" -> {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("scope", "province");
                payload.put("provinceId", provinceId);
                payload.put("smooth", dashboardCacheService.getSmoothRows());
                yield payload;
            }
            case "category" -> {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("scope", "category");
                payload.put("platformId", platformId);
                payload.put("categoryName", categoryName);
                payload.put("line", dashboardCacheService.getLineRows(platformId));
                yield payload;
            }
            default -> dashboardCacheService.buildHomeSnapshot(platformId, userId);
        };
    }
}
