package com.iptnet.consume.service.rocketmq.impl;

import com.iptnet.consume.service.dashboard.DashboardAggregateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@RocketMQMessageListener(topic = "dashboard_aggregate_topic", consumerGroup = "dashboard-aggregate-consumer-group")
public class RocketDashboardAggregator implements RocketMQListener<Map<String, Object>> {

    private final DashboardAggregateService dashboardAggregateService;

    @Override
    public void onMessage(Map<String, Object> msg) {
        Integer userId = asNullableInt(msg == null ? null : msg.get("userId"));
        String reason = msg == null ? "UNKNOWN" : String.valueOf(msg.getOrDefault("reason", "UNKNOWN"));
        dashboardAggregateService.rebuildAndPublish(userId, reason);
    }

    private Integer asNullableInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            int parsed = number.intValue();
            return parsed > 0 ? parsed : null;
        }
        try {
            int parsed = Integer.parseInt(String.valueOf(value));
            return parsed > 0 ? parsed : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
