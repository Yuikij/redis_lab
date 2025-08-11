package com.soukon.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class JedisConfig {
    // 通过 Spring Data Redis 的 JedisConnectionFactory 统一配置，不再直接暴露 JedisPool
}


