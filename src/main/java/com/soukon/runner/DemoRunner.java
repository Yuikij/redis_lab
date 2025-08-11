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
        System.out.println("===== Redisson 示例 =====");
        boolean locked = redissonDemoService.tryWithLock("demo");
        System.out.println("分布式锁获取结果: " + locked);
        redissonDemoService.cacheWithTtl("demo:map", "f1", "v1");
        redissonDemoService.initBloom("demo:bloom");
        System.out.println("布隆过滤器包含 f1 ?: " + redissonDemoService.bloomMightContain("demo:bloom", "f1"));
        System.out.println("限流获取令牌: " + redissonDemoService.tryAcquireRate("api"));

        System.out.println("===== Jedis 示例 =====");
        System.out.println("字符串读写: " + jedisDemoService.setAndGet("jedis:k", "v"));
        System.out.println("管道批量结果: " + jedisDemoService.pipelineDemo());
        System.out.println("事务执行结果: " + jedisDemoService.transactionDemo("sku1"));

        System.out.println("===== Lettuce 示例 =====");
        System.out.println("同步读写: " + lettuceDemoService.syncSetGet("lettuce:k", "v"));
        Mono<String> asyncVal = lettuceDemoService.asyncGet("lettuce:k");
        System.out.println("异步读取: " + asyncVal.block());
        System.out.println("响应式读写: " + lettuceDemoService.reactiveSetGet("lettuce:rk", "rv").block());
    }
}


