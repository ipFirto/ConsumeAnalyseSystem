package com.iptnet.consume.service.redis.impl;

import com.iptnet.consume.service.redis.RedisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class RedisServiceImpl implements RedisService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    public void setCode(String email, String code) {
        String key = "user:email:code:" + email;
        redisTemplate.opsForValue().set(key, code, 5, TimeUnit.MINUTES);
    }

    public String getCode(String email) {
        return redisTemplate.opsForValue()
                .get("user:email:code:" + email);
    }

    public void deleteCode(String email) {
        redisTemplate.delete("user:email:code:" + email);
    }
}
