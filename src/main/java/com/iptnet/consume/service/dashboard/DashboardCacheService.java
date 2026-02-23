package com.iptnet.consume.service.dashboard;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iptnet.consume.mapper.ConsumeMapper;
import com.iptnet.consume.mapper.PlatformDataMapper;
import com.iptnet.consume.mapper.TransactionOrderMapper;
import com.iptnet.consume.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardCacheService {

    private static final String KEY_HOME_PLATFORM_IDS = "dash:v3:home:platform:ids";
    private static final String KEY_HOME_BAR = "dash:v3:home:bar";
    private static final String KEY_HOME_SMOOTH = "dash:v3:home:smooth";
    private static final String KEY_HOME_LINE_PREFIX = "dash:v3:home:line:";
    private static final String KEY_USER_RECENT_PREFIX = "dash:v3:user:recent30:";
    private static final Duration USER_RECENT_TTL = Duration.ofHours(6);

    private static final TypeReference<List<Integer>> LIST_INT = new TypeReference<>() {};
    private static final TypeReference<List<HashMap<String, Object>>> LIST_MAP = new TypeReference<>() {};

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final ConsumeMapper consumeMapper;
    private final TransactionOrderMapper transactionOrderMapper;
    private final PlatformDataMapper platformDataMapper;
    private final UserMapper userMapper;

    public void warmupRequiredData() {
        long start = System.currentTimeMillis();
        List<Integer> platformIds = getPlatformIds();
        refreshHomeCharts(platformIds);

        List<Integer> userIds = userMapper.listActiveUserIds();
        for (Integer userId : userIds) {
            if (userId != null && userId > 0) {
                refreshRecentOrders(userId);
            }
        }
        long cost = System.currentTimeMillis() - start;
        log.info("dashboard cache warmup finished, platforms={}, users={}, costMs={}",
                platformIds.size(), userIds.size(), cost);
    }

    public void refreshHomeCharts() {
        refreshHomeCharts(getPlatformIds());
    }

    public void refreshHomeCharts(List<Integer> platformIds) {
        List<Integer> safePlatformIds = normalizePlatformIds(platformIds);
        writeJson(KEY_HOME_PLATFORM_IDS, safePlatformIds);
        writeJson(KEY_HOME_BAR, fetchBarCountsFromDb(safePlatformIds));
        writeJson(KEY_HOME_SMOOTH, safeListMap(consumeMapper.showSmoothByOriginal()));

        for (Integer platformId : safePlatformIds) {
            writeJson(lineKey(platformId), safeListMap(consumeMapper.showLineByPlatform(platformId)));
        }
    }

    public void refreshRecentOrders(Integer userId) {
        if (userId == null || userId <= 0) {
            return;
        }
        List<HashMap<String, Object>> rows = transactionOrderMapper.selectRecentOrders(userId, 30, null);
        saveRecentOrders(userId, rows);
    }

    public void saveRecentOrders(Integer userId, List<HashMap<String, Object>> rows) {
        if (userId == null || userId <= 0) {
            return;
        }
        writeJson(KEY_USER_RECENT_PREFIX + userId, safeListMap(rows), USER_RECENT_TTL);
    }

    public Map<String, Object> buildHomeSnapshot(Integer platformId, Integer userId) {
        return buildHomePayload(platformId, userId, true);
    }

    public Map<String, Object> buildHomePatch(Integer platformId, Integer userId) {
        return buildHomePayload(platformId, userId, false);
    }

    public List<Integer> getPlatformIds() {
        List<Integer> cached = readJson(KEY_HOME_PLATFORM_IDS, LIST_INT);
        if (cached != null && !cached.isEmpty()) {
            return normalizePlatformIds(cached);
        }

        List<Integer> ids = platformDataMapper.platformIds();
        List<Integer> safe = normalizePlatformIds(ids);
        writeJson(KEY_HOME_PLATFORM_IDS, safe);
        return safe;
    }

    public List<Integer> getBarCounts() {
        List<Integer> cached = readJson(KEY_HOME_BAR, LIST_INT);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }
        List<Integer> fresh = fetchBarCountsFromDb(getPlatformIds());
        writeJson(KEY_HOME_BAR, fresh);
        return fresh;
    }

    public List<HashMap<String, Object>> getSmoothRows() {
        List<HashMap<String, Object>> cached = readJson(KEY_HOME_SMOOTH, LIST_MAP);
        if (cached != null) {
            return cached;
        }
        List<HashMap<String, Object>> fresh = safeListMap(consumeMapper.showSmoothByOriginal());
        writeJson(KEY_HOME_SMOOTH, fresh);
        return fresh;
    }

    public List<HashMap<String, Object>> getLineRows(Integer platformId) {
        Integer safePlatformId = normalizePlatformId(platformId, getPlatformIds());
        String key = lineKey(safePlatformId);

        List<HashMap<String, Object>> cached = readJson(key, LIST_MAP);
        if (cached != null) {
            return cached;
        }
        List<HashMap<String, Object>> fresh = safeListMap(consumeMapper.showLineByPlatform(safePlatformId));
        writeJson(key, fresh);
        return fresh;
    }

    public Map<String, List<HashMap<String, Object>>> getAllLineRows() {
        Map<String, List<HashMap<String, Object>>> lineByPlatform = new LinkedHashMap<>();
        for (Integer platformId : getPlatformIds()) {
            lineByPlatform.put(String.valueOf(platformId), getLineRows(platformId));
        }
        return lineByPlatform;
    }

    public List<HashMap<String, Object>> getRecentOrders(Integer userId) {
        if (userId == null || userId <= 0) {
            return List.of();
        }
        List<HashMap<String, Object>> cached = readJson(KEY_USER_RECENT_PREFIX + userId, LIST_MAP);
        if (cached != null) {
            return cached;
        }
        refreshRecentOrders(userId);
        List<HashMap<String, Object>> fresh = readJson(KEY_USER_RECENT_PREFIX + userId, LIST_MAP);
        return fresh == null ? List.of() : fresh;
    }

    private Map<String, Object> buildHomePayload(Integer platformId, Integer userId, boolean includeSnapshotMeta) {
        List<Integer> platformIds = getPlatformIds();
        Integer safePlatformId = normalizePlatformId(platformId, platformIds);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("platformIds", platformIds);
        payload.put("bar", getBarCounts());
        payload.put("smooth", getSmoothRows());
        payload.put("linePlatformId", safePlatformId);
        payload.put("line", getLineRows(safePlatformId));
        payload.put("lineByPlatform", getAllLineRows());
        if (userId != null && userId > 0) {
            payload.put("recentOrders", getRecentOrders(userId));
        }
        payload.put("generatedAt", Instant.now().toString());
        if (includeSnapshotMeta) {
            payload.put("source", "redis");
        }
        return payload;
    }

    private List<Integer> fetchBarCountsFromDb(List<Integer> platformIds) {
        List<Integer> fallback = new ArrayList<>(Collections.nCopies(platformIds.size(), 0));

        try {
            List<String> rows = consumeMapper.showBarByPlatform();
            if (rows == null || rows.isEmpty() || rows.getFirst() == null || rows.getFirst().isBlank()) {
                return fallback;
            }

            List<Integer> parsed = objectMapper.readValue(rows.getFirst(), LIST_INT);
            if (parsed == null || parsed.isEmpty()) {
                return fallback;
            }

            if (parsed.size() < platformIds.size()) {
                ArrayList<Integer> padded = new ArrayList<>(parsed);
                while (padded.size() < platformIds.size()) {
                    padded.add(0);
                }
                return padded;
            }
            if (parsed.size() > platformIds.size()) {
                return new ArrayList<>(parsed.subList(0, platformIds.size()));
            }
            return parsed;
        } catch (Exception e) {
            log.warn("load bar chart from db failed", e);
            return fallback;
        }
    }

    private Integer normalizePlatformId(Integer platformId, List<Integer> platformIds) {
        if (platformId != null && platformIds.contains(platformId)) {
            return platformId;
        }

        Integer preferred = resolveDefaultHomeLinePlatformId(platformIds);
        if (preferred != null && platformIds.contains(preferred)) {
            return preferred;
        }

        return platformIds.isEmpty() ? 1 : platformIds.getFirst();
    }

    private Integer resolveDefaultHomeLinePlatformId(List<Integer> platformIds) {
        if (platformIds == null || platformIds.isEmpty()) {
            return null;
        }
        try {
            List<HashMap<String, Object>> platforms = platformDataMapper.platformList();
            if (platforms == null || platforms.isEmpty()) {
                return platformIds.getFirst();
            }

            for (Map<String, Object> row : platforms) {
                Integer id = parseInt(row.get("id"));
                if (id == null || !platformIds.contains(id)) {
                    continue;
                }
                String code = String.valueOf(row.getOrDefault("code", "")).trim().toLowerCase();
                String name = String.valueOf(row.getOrDefault("name", "")).trim();
                if ("douyin".equals(code) || "抖音".equals(name)) {
                    return id;
                }
            }
        } catch (Exception e) {
            log.warn("resolve default home line platform failed", e);
        }
        return platformIds.getFirst();
    }

    private Integer parseInt(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number num) {
            int v = num.intValue();
            return v > 0 ? v : null;
        }
        try {
            int v = Integer.parseInt(String.valueOf(raw));
            return v > 0 ? v : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<Integer> normalizePlatformIds(List<Integer> ids) {
        List<Integer> source = ids == null ? List.of() : ids;
        ArrayList<Integer> cleaned = source.stream()
                .filter(Objects::nonNull)
                .map(Integer::intValue)
                .filter(id -> id > 0)
                .distinct()
                .sorted()
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

        if (!cleaned.isEmpty()) {
            return cleaned;
        }
        return new ArrayList<>(List.of(1, 2, 3, 4, 5));
    }

    private <T> T readJson(String key, TypeReference<T> typeReference) {
        try {
            String raw = redis.opsForValue().get(key);
            if (raw == null || raw.isBlank()) {
                return null;
            }
            return objectMapper.readValue(raw, typeReference);
        } catch (Exception e) {
            log.warn("read redis key={} failed", key, e);
            return null;
        }
    }

    private void writeJson(String key, Object value) {
        writeJson(key, value, null);
    }

    private void writeJson(String key, Object value, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(value);
            if (ttl == null || ttl.isZero() || ttl.isNegative()) {
                redis.opsForValue().set(key, json);
            } else {
                redis.opsForValue().set(key, json, ttl);
            }
        } catch (Exception e) {
            log.warn("write redis key={} failed", key, e);
        }
    }

    private List<HashMap<String, Object>> safeListMap(List<? extends Map<String, Object>> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        ArrayList<HashMap<String, Object>> list = new ArrayList<>(raw.size());
        for (Map<String, Object> item : raw) {
            list.add(item == null ? new HashMap<>() : new HashMap<>(item));
        }
        return list;
    }

    private String lineKey(Integer platformId) {
        return KEY_HOME_LINE_PREFIX + platformId;
    }
}
