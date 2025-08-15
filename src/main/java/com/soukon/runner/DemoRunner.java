package com.soukon.runner;

import com.soukon.service.JedisDemoService;
import com.soukon.service.LeaderboardService;
import com.soukon.service.LettuceDemoService;
import com.soukon.service.RedissonDemoService;
import com.soukon.service.SkipListDemoService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class DemoRunner implements CommandLineRunner {

    private final RedissonDemoService redissonDemoService;
    private final JedisDemoService jedisDemoService;
    private final LettuceDemoService lettuceDemoService;
    private final SkipListDemoService skipListDemoService;
    private final LeaderboardService leaderboardService;

    public DemoRunner(RedissonDemoService redissonDemoService,
                      JedisDemoService jedisDemoService,
                      LettuceDemoService lettuceDemoService,
                      SkipListDemoService skipListDemoService,
                      LeaderboardService leaderboardService) {
        this.redissonDemoService = redissonDemoService;
        this.jedisDemoService = jedisDemoService;
        this.lettuceDemoService = lettuceDemoService;
        this.skipListDemoService = skipListDemoService;
        this.leaderboardService = leaderboardService;
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

        System.out.println("===== Redis跳表数据结构演示 =====");
        skipListDemoService.runAllDemos();

        System.out.println("===== Redis排行榜演示 =====");
        runLeaderboardDemo();
    }

    /**
     * 演示 Redis 排行榜功能
     */
    private void runLeaderboardDemo() {
        String gameLeaderboard = "game-score";
        
        // 清空之前的数据
        leaderboardService.clearLeaderboard(gameLeaderboard);
        
        System.out.println("1. 添加玩家分数");
        leaderboardService.setScore(gameLeaderboard, "Alice", 1500.0);
        leaderboardService.setScore(gameLeaderboard, "Bob", 1200.0);
        leaderboardService.setScore(gameLeaderboard, "Charlie", 1800.0);
        leaderboardService.setScore(gameLeaderboard, "David", 1000.0);
        leaderboardService.setScore(gameLeaderboard, "Eve", 1600.0);
        
        System.out.println("2. 查看TOP 3排行榜:");
        var top3 = leaderboardService.getTopN(gameLeaderboard, 3);
        top3.forEach(System.out::println);
        
        System.out.println("\n3. 查看Alice的排名和分数:");
        Long aliceRank = leaderboardService.getUserRank(gameLeaderboard, "Alice");
        Double aliceScore = leaderboardService.getUserScore(gameLeaderboard, "Alice");
        System.out.println("Alice: 第" + aliceRank + "名, " + aliceScore + "分");
        
        System.out.println("\n4. Alice获得额外200分:");
        Double newScore = leaderboardService.addScore(gameLeaderboard, "Alice", 200.0);
        System.out.println("Alice新分数: " + newScore);
        Long newRank = leaderboardService.getUserRank(gameLeaderboard, "Alice");
        System.out.println("Alice新排名: 第" + newRank + "名");
        
        System.out.println("\n5. 查看Alice周围的玩家(前后各1名):");
        var neighbors = leaderboardService.getUserNeighbors(gameLeaderboard, "Alice", 1);
        neighbors.forEach(System.out::println);
        
        System.out.println("\n6. 查看分数在1400-1600范围的玩家:");
        var midRange = leaderboardService.getUsersByScoreRange(gameLeaderboard, 1400.0, 1600.0);
        midRange.forEach(System.out::println);
        
        System.out.println("\n7. 当前排行榜总人数: " + leaderboardService.getTotalUsers(gameLeaderboard));
        
        System.out.println("\n8. 演示日榜功能:");
        leaderboardService.addDailyScore("daily-game", "Player1", 500.0);
        leaderboardService.addDailyScore("daily-game", "Player2", 300.0);
        leaderboardService.addDailyScore("daily-game", "Player1", 200.0); // 累加
        
        System.out.println("今日TOP 2:");
        var todayTop = leaderboardService.getTodayTopN("daily-game", 2);
        todayTop.forEach(System.out::println);
    }
}


