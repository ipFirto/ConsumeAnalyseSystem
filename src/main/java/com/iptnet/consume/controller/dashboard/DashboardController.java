package com.iptnet.consume.controller.dashboard;

import com.iptnet.consume.dao.*;
import com.iptnet.consume.service.dashboard.*;
import com.iptnet.consume.utils.ThreadLocalUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardEventStore eventStore;
    private final DashboardStreamService streamService;
    private final DashboardQueryService queryService;

    public DashboardController(DashboardEventStore eventStore,
                               DashboardStreamService streamService,
                               DashboardQueryService queryService) {
        this.eventStore = eventStore;
        this.streamService = streamService;
        this.queryService = queryService;
    }

    // 1) 首屏全量
    @GetMapping("/snapshot")
    public ApiResponse<SnapshotResponse> snapshot(
            @RequestParam(defaultValue = "home") String scope,
            @RequestParam(required = false) Integer platformId,
            @RequestParam(required = false) String categoryName,
            @RequestParam(required = false) Integer provinceId
    ) {
        Object data = queryService.snapshot(scope, platformId, categoryName, provinceId, currentUidOrNull());
        String cursor = String.valueOf(eventStore.currentCursor());
        return ApiResponse.ok(cursor, new SnapshotResponse(scope, data));
    }

    // 2) SSE 持续推送
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam(required = false) String topics, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Connection", "keep-alive");
        Set<String> t = TopicMatcher.parseTopics(topics);
        return streamService.openStream(t);
    }

    // 3) 断线补偿（按 cursor 回放）
    @GetMapping("/delta")
    public ApiResponse<DeltaResponse> delta(
            @RequestParam(name = "since") long since,
            @RequestParam(required = false) String topics,
            @RequestParam(defaultValue = "500") int limit
    ) {
        Set<String> t = TopicMatcher.parseTopics(topics);
        var events = eventStore.readAfter(since, limit).stream()
                .filter(e -> TopicMatcher.match(t, e.topic()) || "system".equals(e.topic()))
                .toList();

        long next = events.isEmpty() ? since : events.get(events.size() - 1).cursor();
        // hasMore：如果 store 里还有更多事件未取（简化判断）
        boolean hasMore = !eventStore.readAfter(next, 1).isEmpty();

        return ApiResponse.ok(String.valueOf(eventStore.currentCursor()),
                new DeltaResponse(events, String.valueOf(next), hasMore));
    }

    // 4) meta：下发配置（可选）
    @GetMapping("/meta")
    public ApiResponse<DashboardMeta> meta() {
        DashboardMeta meta = new DashboardMeta(
                15000,
                30000,
                List.of("home")
        );
        return ApiResponse.ok(String.valueOf(eventStore.currentCursor()), meta);
    }

    private Integer currentUidOrNull() {
        try {
            Map<String, Object> claims = ThreadLocalUtil.get();
            if (claims == null || claims.get("id") == null) {
                return null;
            }
            Object idObj = claims.get("id");
            if (idObj instanceof Number number) {
                int uid = number.intValue();
                return uid > 0 ? uid : null;
            }
            int uid = Integer.parseInt(String.valueOf(idObj));
            return uid > 0 ? uid : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
