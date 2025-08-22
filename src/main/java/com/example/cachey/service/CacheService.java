package com.example.cachey.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class CacheService {

    private final RedisTemplate<String, String> redisTemplate;

    @Autowired
    public CacheService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void setValue(String key, String value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public void setValue(String key, String value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    public String getValue(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public boolean deleteKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.delete(key));
    }

    public boolean isRedisConnected() {
        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public long getKeyCount() {
        try {
            return redisTemplate.getConnectionFactory().getConnection().dbSize();
        } catch (Exception e) {
            return -1;
        }
    }
}