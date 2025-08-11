package com.soukon.service;

import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RMapCache;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class RedissonDemoService {
    private final RedissonClient redissonClient;              // 用于并发原语与分布式对象
    private final StringRedisTemplate redissonTemplate;       // 通过 Spring Data 使用 Redisson 连接工厂

    public RedissonDemoService(RedissonClient redissonClient,
                               @Qualifier("redissonStringRedisTemplate") StringRedisTemplate redissonTemplate) {
        this.redissonClient = redissonClient;
        this.redissonTemplate = redissonTemplate;
    }

    /**
     * 分布式锁示例
     */
    public boolean tryWithLock(String key) throws InterruptedException {
        RLock lock = redissonClient.getLock("lock:" + key);
        if (lock.tryLock(3, 10, TimeUnit.SECONDS)) {
            try {
                // 也可以通过 redissonTemplate 做一些 KV 操作
                redissonTemplate.opsForValue().set("lock:flag:" + key, "1");
                return true;
            } finally {
                lock.unlock();
            }
        }
        return false;
    }

    /**
     * 带 TTL 与 MaxIdle 的分布式 MapCache
     */
    public void cacheWithTtl(String mapKey, String field, String value) {
        RMapCache<String, String> map = redissonClient.getMapCache(mapKey);
        map.put(field, value, 5, TimeUnit.MINUTES, 2, TimeUnit.MINUTES);
    }

    /**
     * 初始化布隆过滤器（若不存在）
     */
    public void initBloom(String name) {
        RBloomFilter<String> bloom = redissonClient.getBloomFilter(name);
        if (!bloom.isExists()) {
            bloom.tryInit(1_000_000, 0.01);
        }
    }

    public boolean bloomMightContain(String name, String value) {
        return redissonClient.getBloomFilter(name).contains(value);
    }

    /**
     * 分布式限流器：整体 1 秒 100 次
     */
    public boolean tryAcquireRate(String name) {
        RRateLimiter limiter = redissonClient.getRateLimiter("rate:" + name);
        limiter.trySetRate(RateType.OVERALL, 100, 1, RateIntervalUnit.SECONDS);
        return limiter.tryAcquire();
    }
}


