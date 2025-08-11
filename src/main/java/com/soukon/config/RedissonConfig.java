package com.soukon.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;

@Configuration
public class RedissonConfig {

    /**
     * 配置 Redisson 客户端
     * - Redisson 提供丰富的分布式并发原语与数据结构（锁、限流、布隆、MapCache 等）
     * - 这里演示单机连接，实际可配置主从、哨兵或集群
     */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(RedisProperties properties) {
        Config config = new Config();
        String host = properties.getHost();
        int port = properties.getPort();
        int db = properties.getDatabase();
        String password = properties.getPassword();
        String address = String.format("redis://%s:%d", host, port);
        var single = config.useSingleServer()
                .setAddress(address)
                .setDatabase(db);
        if (password != null && !password.isEmpty()) {
            single.setPassword(password);
        }
        return Redisson.create(config);
    }
}


