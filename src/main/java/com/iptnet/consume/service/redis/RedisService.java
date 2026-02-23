package com.iptnet.consume.service.redis;

public interface RedisService {
    void setCode(String email, String code);
    String getCode(String email);
    void deleteCode(String email);
}
