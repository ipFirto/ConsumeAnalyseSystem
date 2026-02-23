package com.iptnet.consume.service.rocketmq.impl;

import com.iptnet.consume.mapper.TransactionOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@RocketMQMessageListener(topic = "delay_close_order_topic", consumerGroup = "test-consumer-group")
public class RocketCloseOrders implements RocketMQListener<Map<String, Object>> {

    private final TransactionOrderMapper transactionOrderMapper;
    private final MqProducer mqProducer;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onMessage(Map<String, Object> msg) {
        String orderNo = String.valueOf(msg.get("orderNo"));
        Map<String, Object> beforeMeta = transactionOrderMapper.selectOrderMeta(orderNo);
        Integer userId = asNullableInt(beforeMeta == null ? null : beforeMeta.get("user_id"));
        Integer productId = asNullableInt(beforeMeta == null ? null : beforeMeta.get("product_id"));
        Integer quantity = asNullableInt(beforeMeta == null ? null : beforeMeta.get("quantity"));
        int safeQuantity = quantity == null || quantity <= 0 ? 1 : quantity;

        int rows = transactionOrderMapper.closeIfUnpaid(orderNo);
        if (rows > 0) {
            if (productId != null && productId > 0) {
                transactionOrderMapper.releaseProductStock(productId, safeQuantity);
            }
            if (userId != null && userId > 0) {
                transactionOrderMapper.insertOrderOperateLog(
                        orderNo,
                        userId,
                        "TIMEOUT_CLOSE",
                        1,
                        3,
                        "SYSTEM",
                        "mq-delay",
                        "Order timeout by delayed mq message, quantity=" + safeQuantity
                );
            }
            log.info("timeout close order success, orderNo={}", orderNo);
            mqProducer.sendDashboardAggregate(userId, "TIMEOUT_DELAY");
        } else {
            log.debug("timeout close order ignored, orderNo={}", orderNo);
        }
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
