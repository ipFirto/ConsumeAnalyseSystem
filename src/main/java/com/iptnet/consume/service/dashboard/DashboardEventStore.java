package com.iptnet.consume.service.dashboard;

import com.iptnet.consume.dao.DashboardEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;

public class DashboardEventStore {

    private final AtomicLong cursorGen = new AtomicLong(1740000000000L); // 你也可以从当前时间毫秒起
    private final NavigableMap<Long, DashboardEvent> store = new ConcurrentSkipListMap<>();
    private final int maxKeep; // 最大保留事件数

    public DashboardEventStore(int maxKeep) {
        this.maxKeep = Math.max(1000, maxKeep);
    }

    public long nextCursor() {
        return cursorGen.incrementAndGet();
    }

    public long currentCursor() {
        return cursorGen.get();
    }

    public DashboardEvent append(DashboardEvent evt) {
        store.put(evt.cursor(), evt);
        trimIfNeeded();
        return evt;
    }

    public List<DashboardEvent> readAfter(long sinceCursor, int limit) {
        int realLimit = Math.min(Math.max(limit, 1), 2000);
        List<DashboardEvent> res = new ArrayList<>(realLimit);
        for (var e : store.tailMap(sinceCursor + 1, true).entrySet()) {
            res.add(e.getValue());
            if (res.size() >= realLimit) break;
        }
        return res;
    }

    public long oldestCursor() {
        var first = store.firstEntry();
        return first == null ? currentCursor() : first.getKey();
    }

    private void trimIfNeeded() {
        while (store.size() > maxKeep) {
            var first = store.firstEntry();
            if (first == null) break;
            store.remove(first.getKey());
        }
    }
}
