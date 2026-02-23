package com.iptnet.consume.service.consume.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iptnet.consume.dto.ConsumeOrderDTO;
import com.iptnet.consume.dto.OrderEvent;
import com.iptnet.consume.mapper.ConsumeMapper;
import com.iptnet.consume.service.consume.ConsumeService;
import com.iptnet.consume.utils.SnowflakeIdGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class ConsumeServiceImpl implements ConsumeService {

    @Autowired
    private ConsumeMapper consumeMapper;
    @Autowired
    private SnowflakeIdGenerator idGenerator;

    private static final String ORDER_QUEUE_KEY = "queue:orders";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public ConsumeServiceImpl(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public void enqueueOrder(Integer uid, String orderNo, Integer productId, Integer cityId,
                             BigDecimal amount, String remark) {
        try {
            OrderEvent evt = new OrderEvent(null, uid, orderNo, productId, cityId, 1, amount, remark, LocalDateTime.now());
            String json = objectMapper.writeValueAsString(evt);
            redis.opsForList().leftPush(ORDER_QUEUE_KEY, json);
        } catch (Exception e) {
            throw new RuntimeException("写入Redis队列失败", e);
        }
    }

    public void autoInsert(Integer userId, String orderNo, Integer productId, Integer cityId, BigDecimal amount, String remark) {
        try {
            consumeMapper.insertConsume(userId, orderNo, productId, cityId, amount, remark);
        } catch (DuplicateKeyException e) {
            // 理论上几乎不会发生（除非多节点 workerId 配错）
            String newOrderNo = String.valueOf(idGenerator.nextId());
            consumeMapper.insertConsume(userId, newOrderNo, productId, cityId, amount, remark);
        }
    }

    @Override
    public List<ConsumeOrderDTO> orderByOrigen(Integer platformId) {
        return consumeMapper.orderByOrigen(platformId);
    }

    @Override
    public List<ConsumeOrderDTO> orderNewByOrigen(Integer platformId, Integer id) {
        return consumeMapper.orderNewByOrigen(platformId,id);
    }

    @Override
    public List<String> showBarByPlatform() {
        return consumeMapper.showBarByPlatform();
    }

    @Override
    public List<Map<String, Object>> showLineByPlatform(Integer platformId) {
        return consumeMapper.showLineByPlatform(platformId);
    }

    @Override
    public List<Map<String, Object>> showSmoothByOriginal() {
        return consumeMapper.showSmoothByOriginal();
    }

    @Override
    public List<Map<String, Object>> showUserOrder(Integer id) {
        return consumeMapper.showUserOrder(id);
    }


}
