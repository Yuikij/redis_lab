package com.soukon;

import com.soukon.service.RedissonDemoService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RedissonDemoServiceTest {

    @BeforeAll
    static void checkRedis(@org.springframework.beans.factory.annotation.Autowired org.springframework.core.env.Environment env) {
        RedisConnectivity.assumeRedis(env);
    }

    @Autowired
    private RedissonDemoService redissonDemoService;

    @Test
    void testLockAndUnlock() throws Exception {
        boolean acquired = redissonDemoService.tryWithLock("test");
        assertTrue(acquired, "应该能拿到分布式锁");
    }

    @Test
    void testMapCacheAndBloomAndRateLimiter() {
        redissonDemoService.cacheWithTtl("test:map", "field", "value");

        redissonDemoService.initBloom("test:bloom");
        boolean may = redissonDemoService.bloomMightContain("test:bloom", "field");
        assertFalse(may, "未添加的元素布隆过滤器通常判定为不存在（可能误判为存在，但概率很低）");

        boolean rateOk = redissonDemoService.tryAcquireRate("api");
        assertTrue(rateOk, "首次获取限流令牌应成功");
    }
}


