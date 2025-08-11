package com.soukon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 应用启动入口
 * - 使用 Spring Boot 自动装配，启动后会运行 {@link com.soukon.runner.DemoRunner}
 * - DemoRunner 中将演示 Redisson、Jedis、Lettuce 三种客户端的核心能力
 */
@SpringBootApplication
public class Main {
    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }
}