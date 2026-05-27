package com.agentoffice.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LRU cache for LLM completion results.
 * Cache key: MD5(systemPrompt + userPrompt + model)
 */
@Slf4j
@Component
public class LLMCache {
    private final int maxSize;
    private final long ttlMillis;
    private final LinkedHashMap<String, CacheEntry> cache;

    public LLMCache(
            @Value("${llm.cache.max-size:10000}") int maxSize,
            @Value("${llm.cache.ttl-seconds:3600}") long ttlSeconds) {
        this.maxSize = maxSize;
        this.ttlMillis = ttlSeconds * 1000;
        this.cache = new LinkedHashMap<>(maxSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                return size() > LLMCache.this.maxSize;
            }
        };
    }

    public String buildKey(String systemPrompt, String userPrompt, String model) {
        String input = systemPrompt + "|||" + userPrompt + "|||" + model;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }

    public synchronized String get(String key) {
        CacheEntry entry = cache.get(key);
        if (entry != null && !entry.isExpired(ttlMillis)) {
            log.debug("Cache hit: {}", key.substring(0, 8));
            return entry.value;
        }
        if (entry != null) {
            cache.remove(key);
        }
        return null;
    }

    public synchronized void put(String key, String value) {
        cache.put(key, new CacheEntry(value, System.currentTimeMillis()));
    }

    public synchronized int size() {
        return cache.size();
    }

    public synchronized void clear() {
        cache.clear();
    }

    private record CacheEntry(String value, long timestamp) {
        boolean isExpired(long ttlMillis) {
            return System.currentTimeMillis() - timestamp > ttlMillis;
        }
    }
}
