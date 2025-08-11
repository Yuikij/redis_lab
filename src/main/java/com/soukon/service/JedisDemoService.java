package com.soukon.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class JedisDemoService {
    private final StringRedisTemplate jedisTemplate; // 基于 Jedis 的模板

    public JedisDemoService(@Qualifier("jedisStringRedisTemplate") StringRedisTemplate jedisTemplate) {
        this.jedisTemplate = jedisTemplate;
    }

    /**
     * 基础字符串读写示例
     */
    public String setAndGet(String key, String value) {
        jedisTemplate.opsForValue().set(key, value);
        return jedisTemplate.opsForValue().get(key);
    }

    /**
     * Pipeline 批量执行示例（通过 Spring Data Redis pipeline）
     */
    public List<Object> pipelineDemo() {
        return jedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            var stringSerializer = jedisTemplate.getStringSerializer();
            connection.stringCommands().set(stringSerializer.serialize("k1"), stringSerializer.serialize("v1"));
            connection.stringCommands().incr(stringSerializer.serialize("count"));
            connection.stringCommands().get(stringSerializer.serialize("k1"));
            return null;
        });
    }

    /**
     * 事务（MULTI/EXEC）示例
     */
    public List<Object> transactionDemo(String sku) {
        return jedisTemplate.execute((RedisCallback<List<Object>>) connection -> {
            var s = jedisTemplate.getStringSerializer();
            connection.multi();
            connection.stringCommands().decrBy(s.serialize("stock:" + sku), 1);
            connection.listCommands().lPush(s.serialize("stock:log"), s.serialize("decrease 1 for " + sku));
            return connection.exec();
        });
    }
}


