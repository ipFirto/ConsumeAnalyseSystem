package com.iptnet.consume.service.dashboard;

import com.iptnet.consume.dao.DashboardEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DashboardEventPublisher {

    private final DashboardEventStore eventStore;
    private final DashboardStreamService streamService;

    public DashboardEvent publishPatch(String topic, String op, Object payload) {
        long cursor = eventStore.nextCursor();
        DashboardEvent event = DashboardEvent.patch(cursor, topic, op, payload);
        eventStore.append(event);
        streamService.publish(event);
        return event;
    }
}
