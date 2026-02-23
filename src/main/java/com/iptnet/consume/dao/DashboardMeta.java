package com.iptnet.consume.dao;

import java.util.List;

public record DashboardMeta(
        long heartbeatMs,
        long reconnectMaxMs,
        List<String> defaultTopics
) {}
