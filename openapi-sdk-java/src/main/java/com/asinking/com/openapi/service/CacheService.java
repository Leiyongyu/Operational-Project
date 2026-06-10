package com.asinking.com.openapi.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 双层缓存服务：Redis 远程缓存（主）+ Guava 本地缓存（降级）。
 *
 * - Redis 可用时：写 Redis + 本地，读 Redis → 降级本地。
 * - Redis 不可用时：自动降级为纯 Guava 本地缓存。
 *
 * 典型用法：
 *   List<Item> items = cacheService.get("overview", 30, TimeUnit.MINUTES, () -> computeOverview());
 */
@Service
public class CacheService {

    private static final Logger LOG = LoggerFactory.getLogger(CacheService.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private volatile Boolean redisAvailable = null;

    /** 本地降级缓存（30 分钟过期，最多 100 个 key） */
    private final Cache<String, Object> localCache = CacheBuilder.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .build();

    public CacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 从缓存获取，若不存在则调用 supplier 计算并缓存。
     *
     * @param key      缓存键
     * @param ttl      过期时间
     * @param unit     时间单位
     * @param supplier 计算函数
     * @param <T>      值类型
     * @return 缓存值或计算结果
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, long ttl, TimeUnit unit, Supplier<T> supplier) {
        // 1. 尝试 Redis
        if (isRedisAvailable()) {
            try {
                T value = (T) redisTemplate.opsForValue().get(key);
                if (value != null) {
                    // 同步更新本地缓存
                    localCache.put(key, value);
                    return value;
                }
            } catch (Exception e) {
                markRedisUnavailable(e);
            }
        }

        // 2. 尝试本地 Guava 缓存
        Object local = localCache.getIfPresent(key);
        if (local != null) {
            return (T) local;
        }

        // 3. 计算并缓存
        T value = supplier.get();
        if (value != null) {
            localCache.put(key, value);
            if (isRedisAvailable()) {
                try {
                    redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(unit.toSeconds(ttl)));
                } catch (Exception e) {
                    LOG.warn("Redis 写入失败 key={}: {}", key, e.getMessage());
                }
            }
        }
        return value;
    }

    /** 清除指定 key 的缓存（Redis + 本地） */
    public void invalidate(String key) {
        localCache.invalidate(key);
        if (isRedisAvailable()) {
            try {
                redisTemplate.delete(key);
            } catch (Exception ignored) {}
        }
    }

    /** 检查 Redis 是否可用（带缓存标记，避免每次都抛异常） */
    private boolean isRedisAvailable() {
        if (redisAvailable != null) return redisAvailable;
        try {
            redisTemplate.opsForValue().get("__health_check__");
            redisAvailable = true;
        } catch (Exception e) {
            redisAvailable = false;
            LOG.warn("Redis 不可用，将使用本地缓存降级: {}", e.getMessage());
        }
        return redisAvailable;
    }

    /** Redis 操作失败时标记不可用 */
    private void markRedisUnavailable(Exception e) {
        if (redisAvailable == null || redisAvailable) {
            redisAvailable = false;
            LOG.warn("Redis 操作异常，切换为本地缓存降级: {}", e.getMessage());
        }
    }

    /** 重置 Redis 状态（可在定时任务中调用以恢复） */
    public void resetRedisStatus() {
        redisAvailable = null;
    }
}
