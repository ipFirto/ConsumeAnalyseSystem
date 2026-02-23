package com.iptnet.consume.service.dashboard;

import com.iptnet.consume.dao.DashboardEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DashboardStreamService {
    private static final Logger log = LoggerFactory.getLogger(DashboardStreamService.class);

    private record Client(String id, Set<String> topics, SseEmitter emitter) {}

    private final ConcurrentHashMap<String, Client> clients = new ConcurrentHashMap<>();
    private final DashboardEventStore eventStore;
    private final long emitterTimeoutMs;

    public DashboardStreamService(DashboardEventStore eventStore, long emitterTimeoutMs) {
        this.eventStore = eventStore;
        this.emitterTimeoutMs = emitterTimeoutMs;
    }

    public SseEmitter openStream(Set<String> topics) {
        String id = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(emitterTimeoutMs); // 0L = 不超时（但代理可能会断）
        Client c = new Client(id, topics, emitter);
        clients.put(id, c);

        emitter.onCompletion(() -> clients.remove(id));
        emitter.onTimeout(() -> clients.remove(id));
        emitter.onError(e -> clients.remove(id));

        // 先发 hello（让前端知道连接建立 & 当前 cursor）
        sendEventSafely(c, DashboardEvent.hello(eventStore.currentCursor()));
        return emitter;
    }

    public void publish(DashboardEvent evt) {
        // 广播给订阅匹配的客户端
        for (Client c : clients.values()) {
            if (TopicMatcher.match(c.topics(), evt.topic())) {
                sendEventSafely(c, evt);
            }
        }
    }

    @Scheduled(fixedDelayString = "${dashboard.sse.heartbeat-ms:15000}")
    public void heartbeat() {
        DashboardEvent hb = DashboardEvent.heartbeat(eventStore.currentCursor());
        for (Client c : clients.values()) {
            sendEventSafely(c, hb);
        }
    }

    private void sendEventSafely(Client c, DashboardEvent evt) {
        try {
            c.emitter().send(
                    SseEmitter.event()
                            .name(evt.type())
                            .id(String.valueOf(evt.cursor()))
                            .data(evt, MediaType.APPLICATION_JSON)
            );
        } catch (Throwable ex) {
            // SSE client disconnected (broken pipe / async request closed), remove it quietly.
            clients.remove(c.id());
            if (ex instanceof Error error) {
                throw error;
            }
            if (!isDisconnectedClientError(ex)) {
                log.warn("Failed to send dashboard SSE event, clientId={}, type={}", c.id(), evt.type(), ex);
            }
        }
    }

    private boolean isDisconnectedClientError(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            String className = t.getClass().getName();
            if (className.contains("AsyncRequestNotUsableException")
                    || className.contains("ClientAbortException")) {
                return true;
            }

            String msg = t.getMessage();
            if (msg == null) {
                continue;
            }
            String lower = msg.toLowerCase();
            if (lower.contains("broken pipe")
                    || lower.contains("connection reset by peer")
                    || lower.contains("forcibly closed")
                    || msg.contains("断开的管道")
                    || msg.contains("连接被对方重置")) {
                return true;
            }
        }
        return false;
    }
}
