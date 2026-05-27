package com.agentoffice.dispatch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe shared context store for cross-Agent data sharing.
 * Lifecycle: bound to a master task, async persisted after completion, retained in memory for 24h.
 */
@Slf4j
@Component
public class GlobalContext {

    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Object>> store = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantReadWriteLock> locks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> timestamps = new ConcurrentHashMap<>();

    private ReentrantReadWriteLock getLock(String taskId) {
        return locks.computeIfAbsent(taskId, k -> new ReentrantReadWriteLock());
    }

    public void initTask(String taskId) {
        store.computeIfAbsent(taskId, k -> new ConcurrentHashMap<>());
        timestamps.put(taskId, System.currentTimeMillis());
    }

    /** 线程安全写入（写锁保护）。 */
    public void put(String taskId, String key, Object value) {
        var lock = getLock(taskId);
        lock.writeLock().lock();
        try {
            store.computeIfAbsent(taskId, k -> new ConcurrentHashMap<>()).put(key, value);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** 线程安全读取（读锁保护）。 */
    @SuppressWarnings("unchecked")
    public <T> T get(String taskId, String key) {
        var lock = getLock(taskId);
        lock.readLock().lock();
        try {
            var map = store.get(taskId);
            return map != null ? (T) map.get(key) : null;
        } finally {
            lock.readLock().unlock();
        }
    }

    /** 批量写入（写锁保护）。 */
    public void putAll(String taskId, java.util.Map<String, Object> data) {
        var lock = getLock(taskId);
        lock.writeLock().lock();
        try {
            store.computeIfAbsent(taskId, k -> new ConcurrentHashMap<>()).putAll(data);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** 批量读取，返回快照副本（读锁保护）。 */
    public java.util.Map<String, Object> getAll(String taskId) {
        var lock = getLock(taskId);
        lock.readLock().lock();
        try {
            var map = store.get(taskId);
            return map != null ? new ConcurrentHashMap<>(map) : java.util.Collections.emptyMap();
        } finally {
            lock.readLock().unlock();
        }
    }

    /** 清理指定任务的上下文数据、时间戳与锁。 */
    public void removeTask(String taskId) {
        var lock = getLock(taskId);
        lock.writeLock().lock();
        try {
            store.remove(taskId);
            timestamps.remove(taskId);
        } finally {
            lock.writeLock().unlock();
            locks.remove(taskId);
        }
    }

    /**
     * Clean entries older than 24 hours.
     */
    public void evictExpired() {
        long now = System.currentTimeMillis();
        timestamps.forEach((taskId, ts) -> {
            if (now - ts > 24 * 60 * 60 * 1000) {
                removeTask(taskId);
                log.debug("Evicted context for task: {}", taskId);
            }
        });
    }
}
