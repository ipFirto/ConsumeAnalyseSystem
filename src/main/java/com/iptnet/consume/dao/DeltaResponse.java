package com.iptnet.consume.dao;

import java.util.List;

public record DeltaResponse(
        List<DashboardEvent> events,
        String nextCursor,
        boolean hasMore
) {}
