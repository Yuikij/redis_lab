package com.soukon.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class LettuceDemoService {
    private final StringRedisTemplate lettuceTemplate; // 基于 Lettuce 的模板

    public LettuceDemoService(@Qualifier("lettuceStringRedisTemplate") StringRedisTemplate lettuceTemplate) {
        this.lettuceTemplate = lettuceTemplate;
    }

    /**
     * 同步 API 示例（通过模板）
     */
    public String syncSetGet(String key, String val) {
        lettuceTemplate.opsForValue().set(key, val);
        return lettuceTemplate.opsForValue().get(key);
    }

    /**
     * 以 Mono 形式返回（演示与 Reactor 的结合）
     */
    public Mono<String> asyncGet(String key) {
        return Mono.fromCallable(() -> lettuceTemplate.opsForValue().get(key));
    }

    /**
     * 响应式演示（用 Mono 包裹同步调用以保持示例一致性）
     */
    public Mono<String> reactiveSetGet(String key, String val) {
        return Mono.fromCallable(() -> {
            lettuceTemplate.opsForValue().set(key, val);
            return lettuceTemplate.opsForValue().get(key);
        });
    }
}


