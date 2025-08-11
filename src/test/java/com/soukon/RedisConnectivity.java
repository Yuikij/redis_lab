package com.soukon;

import org.junit.jupiter.api.Assumptions;
import org.springframework.core.env.Environment;

import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * 简单的 Redis 可连接性检查工具：
 * - 支持固定本地检查（assumeLocalRedis）
 * - 支持根据 Spring 配置读取 host/port 的检查（assumeRedis）
 */
public final class RedisConnectivity {
    private RedisConnectivity() {}

    public static void assumeLocalRedis() {
        boolean ok = canConnect("127.0.0.1", 6379, 300);
        Assumptions.assumeTrue(ok, "本地 127.0.0.1:6379 未启动，跳过集成测试");
    }

    public static void assumeRedis(Environment env) {
        String host = env.getProperty("spring.data.redis.host", "127.0.0.1");
        int port = Integer.parseInt(env.getProperty("spring.data.redis.port", "6379"));
        String timeoutStr = env.getProperty("spring.data.redis.timeout", "3000");
        int timeout = 3000;
        try {
            timeout = Integer.parseInt(timeoutStr.replaceAll("[^0-9]", ""));
        } catch (Exception ignored) {}
        boolean ok = canConnect(host, port, timeout);
        Assumptions.assumeTrue(ok, String.format("%s:%d 未能连接，跳过集成测试", host, port));
    }

    private static boolean canConnect(String host, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}


