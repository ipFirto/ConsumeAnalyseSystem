package com.iptnet.consume.dao;

import java.util.List;
import java.util.Map;

public record DashboardChartAggregate(
        List<Integer> bar,
        List<Map<String, Object>> smooth,
        Map<Integer, List<Map<String, Object>>> lineByPlatform,
        Map<String, Object> analytics
) {
}
