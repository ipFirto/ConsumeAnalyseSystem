package com.iptnet.consume.dao;

import java.util.List;

public record SnapshotResponse(
        String scope,
        Object snapshot
) {}