package com.soukon.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class LettuceConfig {
    // 使用 Spring Data Redis 的连接工厂与模板，移除直接使用 lettuce-core 的 Bean
}


