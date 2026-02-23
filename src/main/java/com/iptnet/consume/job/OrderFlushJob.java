//package com.iptnet.consume.job;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.iptnet.consume.dto.OrderEvent;
//import com.iptnet.consume.service.consume.ConsumeService;
//import org.springframework.data.redis.core.StringRedisTemplate;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Component;
//
//import java.util.List;
//
//@Component
//public class OrderFlushJob {
//
//    private static final String ORDER_QUEUE_KEY = "queue:orders";
//
//    private final StringRedisTemplate redis;
//    private final ObjectMapper objectMapper;
//    private final ConsumeService consumeService;
//
//    public OrderFlushJob(StringRedisTemplate redis, ObjectMapper objectMapper, ConsumeService consumeService) {
//        this.redis = redis;
//        this.objectMapper = objectMapper;
//        this.consumeService = consumeService;
//    }
//
//    // 每 2 秒刷一次；你也可以改成 1s/5s
//    @Scheduled(fixedDelay = 2000)
//    public void flushOrdersToMysql() {
//        int batchSize = 200;
//
//        for (int i = 0; i < batchSize; i++) {
//            String json = redis.opsForList().rightPop("queue:orders"); // 一次只 pop 一个
//            if (json == null) {
//                break; // 队列空了就停止
//            }
//
//            try {
//                OrderEvent evt = objectMapper.readValue(json, OrderEvent.class);
//
//                consumeService.autoInsert(
//                        evt.getUserId(),
//                        evt.getOrderNo(),
//                        evt.getProductId(),
//                        evt.getCityId(),
//                        evt.getAmount(),
//                        evt.getRemark()
//                );
//            } catch (Exception e) {
//                // ❗最简单兜底：写回队列，避免丢（可能导致顺序变化）
//                redis.opsForList().rightPush("queue:orders", json);
//                e.printStackTrace();
//                break; // 当前批次先停，避免一直失败循环
//            }
//        }
//    }
//}