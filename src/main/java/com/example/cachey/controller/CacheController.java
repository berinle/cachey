package com.example.cachey.controller;

import com.example.cachey.service.CacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/cache")
public class CacheController {

    private final CacheService cacheService;

    @Autowired
    public CacheController(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    @PostMapping("/{key}")
    public ResponseEntity<String> setValue(@PathVariable String key, @RequestBody String value) {
        cacheService.setValue(key, value);
        return ResponseEntity.ok("Value set for key: " + key);
    }

    @PostMapping("/{key}/ttl/{seconds}")
    public ResponseEntity<String> setValueWithTTL(@PathVariable String key, 
                                                  @PathVariable long seconds, 
                                                  @RequestBody String value) {
        cacheService.setValue(key, value, seconds, TimeUnit.SECONDS);
        return ResponseEntity.ok("Value set for key: " + key + " with TTL: " + seconds + "s");
    }

    @GetMapping("/{key}")
    public ResponseEntity<String> getValue(@PathVariable String key) {
        String value = cacheService.getValue(key);
        return value != null ? ResponseEntity.ok(value) : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<String> deleteKey(@PathVariable String key) {
        boolean deleted = cacheService.deleteKey(key);
        return ResponseEntity.ok(deleted ? "Key deleted: " + key : "Key not found: " + key);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealth() {
        Map<String, Object> response = new HashMap<>();
        boolean connected = cacheService.isRedisConnected();
        long keyCount = cacheService.getKeyCount();
        
        response.put("redis_connected", connected);
        response.put("total_keys", keyCount);
        response.put("status", connected ? "UP" : "DOWN");
        
        return connected ? ResponseEntity.ok(response) : ResponseEntity.status(503).body(response);
    }

    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("pong");
    }
}