package com.iptnet.consume.service.rocketmq.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class MqProducer {

    private final RocketMQTemplate rocketMQTemplate;
    private static final String DELAY_CLOSE_TOPIC = "delay_close_order_topic";
    private static final String DASHBOARD_AGGREGATE_TOPIC = "dashboard_aggregate_topic";

    public void sendDelayCloseOrder(String orderNo) {
        Map<String, Object> map = new HashMap<>();
        map.put("orderNo", orderNo);

        Message<Map<String, Object>> msg = MessageBuilder
                .withPayload(map)
                .setHeader("KEYS", orderNo)
                .build();

        int delayLevel = 6; // about 2 minutes in default RocketMQ delay level config
        try {
            rocketMQTemplate.syncSend(DELAY_CLOSE_TOPIC, msg, 3000, delayLevel);
        } catch (Exception e) {
            // If MQ is unavailable, order timeout will still be handled by scheduled scanner.
            log.warn("send delay close message failed, orderNo={}", orderNo, e);
        }
    }

    public void sendDashboardAggregate(Integer userId, String reason) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("reason", reason == null || reason.isBlank() ? "ORDER_EVENT" : reason);
        payload.put("ts", System.currentTimeMillis());
        if (userId != null && userId > 0) {
            payload.put("userId", userId);
        }

        String key = (userId == null ? "global" : String.valueOf(userId)) + ":" + payload.get("ts");
        Message<Map<String, Object>> msg = MessageBuilder
                .withPayload(payload)
                .setHeader("KEYS", key)
                .build();

        try {
            rocketMQTemplate.syncSend(DASHBOARD_AGGREGATE_TOPIC, msg, 2000);
        } catch (Exception e) {
            log.warn("send dashboard aggregate message failed, userId={}, reason={}", userId, reason, e);
        }
    }
}
