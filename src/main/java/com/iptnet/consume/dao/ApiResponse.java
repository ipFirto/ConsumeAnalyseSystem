package com.iptnet.consume.dao;

import java.time.Instant;

public record ApiResponse<T>(
        int code,
        String message,
        String serverTime,
        String cursor,
        T data
) {
    public static <T> ApiResponse<T> ok(String cursor, T data) {
        return new ApiResponse<>(0, "ok", Instant.now().toString(), cursor, data);
    }
}