package com.iptnet.consume.dao;


import java.time.Instant;

public record DashboardEvent(
        long cursor,           // 递增游标
        String type,           // hello|patch|invalidate|heartbeat|error
        String topic,          // home / platform:2 / category:phone / province:江西 等
        String op,             // merge|replace|remove|noop...
        String ts,             // ISO 时间
        Object payload         // 事件内容（任意对象，自动 JSON）
) {
    public static DashboardEvent patch(long cursor, String topic, String op, Object payload) {
        return new DashboardEvent(cursor, "patch", topic, op, Instant.now().toString(), payload);
    }

    public static DashboardEvent heartbeat(long cursor) {
        return new DashboardEvent(cursor, "heartbeat", "system", "noop", Instant.now().toString(), null);
    }

    public static DashboardEvent hello(long cursor) {
        return new DashboardEvent(cursor, "hello", "system", "noop", Instant.now().toString(), null);
    }
}