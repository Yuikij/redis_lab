package com.soukon.runner;

import com.soukon.service.JedisDemoService;
import com.soukon.service.LettuceDemoService;
import com.soukon.service.RedissonDemoService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class DemoRunner implements CommandLineRunner {

    private final RedissonDemoService redissonDemoService;
    private final JedisDemoService jedisDemoService;
    private final LettuceDemoService lettuceDemoService;

    public DemoRunner(RedissonDemoService redissonDemoService,
                      JedisDemoService jedisDemoService,
                      LettuceDemoService lettuceDemoService) {
        this.redissonDemoService = redissonDemoService;
        this.jedisDemoService = jedisDemoService;
        this.lettuceDemoService = lettuceDemoService;
    }

    @Override
    public void run(String... args) throws Exception {
        // 简单演示：启动时打印各客户端的关键能力
        System.out.println("===== Redisson Demo =====");
        boolean locked = redissonDemoService.tryWithLock("demo");
        System.out.println("lock acquired: " + locked);
        redissonDemoService.cacheWithTtl("demo:map", "f1", "v1");
        redissonDemoService.initBloom("demo:bloom");
        System.out.println("bloom contains f1? " + redissonDemoService.bloomMightContain("demo:bloom", "f1"));
        System.out.println("rate acquire: " + redissonDemoService.tryAcquireRate("api"));

        System.out.println("===== Jedis Demo =====");
        System.out.println("set/get: " + jedisDemoService.setAndGet("jedis:k", "v"));
        System.out.println("pipeline: " + jedisDemoService.pipelineDemo());
        System.out.println("transaction: " + jedisDemoService.transactionDemo("sku1"));

        System.out.println("===== Lettuce Demo =====");
        System.out.println("sync: " + lettuceDemoService.syncSetGet("lettuce:k", "v"));
        Mono<String> asyncVal = lettuceDemoService.asyncGet("lettuce:k");
        System.out.println("async: " + asyncVal.block());
        System.out.println("reactive: " + lettuceDemoService.reactiveSetGet("lettuce:rk", "rv").block());
    }
}


