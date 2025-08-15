package com.soukon.datastructure;

/**
 * 跳表演示程序
 * 
 * 独立运行的演示程序，展示Redis跳表的各种操作
 */
public class SkipListDemo {
    
    public static void main(String[] args) {
        System.out.println("=== Redis跳表Java实现演示 ===\n");
        
        // 创建跳表实例
        SkipList<String> skipList = new SkipList<>();
        
        // 1. 基本操作演示
        demonstrateBasicOperations(skipList);
        
        // 2. 范围查询演示
        demonstrateRangeQuery(skipList);
        
        // 3. 排名查询演示
        demonstrateRankQuery(skipList);
        
        // 4. Redis ZSET命令对应演示
        demonstrateRedisZSetOperations();
        
        // 5. 性能测试
        demonstratePerformance();
    }
    
    /**
     * 基本操作演示
     */
    private static void demonstrateBasicOperations(SkipList<String> skipList) {
        System.out.println("1. === 基本操作演示 ===");
        
        // 插入操作
        System.out.println("插入元素：");
        skipList.insert(1.0, "Alice");
        skipList.insert(2.5, "Bob");
        skipList.insert(1.8, "Charlie");
        skipList.insert(3.2, "David");
        skipList.insert(0.5, "Eve");
        
        System.out.printf("当前跳表长度: %d, 最高层级: %d%n", 
                         skipList.getLength(), skipList.getLevel());
        
        // 查找操作
        System.out.println("\n查找操作：");
        SkipListNode<String> found = skipList.search(2.5, "Bob");
        System.out.println("查找 score=2.5, name=Bob: " + 
                          (found != null ? "找到" : "未找到"));
        
        found = skipList.search(10.0, "NotExist");
        System.out.println("查找 score=10.0, name=NotExist: " + 
                          (found != null ? "找到" : "未找到"));
        
        // 删除操作
        System.out.println("\n删除操作：");
        boolean deleted = skipList.delete(1.8, "Charlie");
        System.out.println("删除 score=1.8, name=Charlie: " + 
                          (deleted ? "成功" : "失败"));
        System.out.printf("删除后跳表长度: %d%n", skipList.getLength());
        
        // 打印跳表结构
        System.out.println("\n跳表结构：");
        skipList.printStructure();
    }
    
    /**
     * 范围查询演示
     */
    private static void demonstrateRangeQuery(SkipList<String> skipList) {
        System.out.println("2. === 范围查询演示 ===");
        
        SkipList<String> rangeList = new SkipList<>();
        
        // 插入测试数据
        String[] names = {"Tom", "Jerry", "Mickey", "Donald", "Goofy"};
        double[] scores = {10.0, 25.0, 18.0, 32.0, 15.0};
        
        System.out.println("插入数据：");
        for (int i = 0; i < names.length; i++) {
            rangeList.insert(scores[i], names[i]);
            System.out.printf("  score=%.1f, name=%s%n", scores[i], names[i]);
        }
        
        // 范围查询
        System.out.println("\n范围查询 [15.0, 30.0]：");
        var rangeResult = rangeList.getByScoreRange(15.0, 30.0);
        for (var node : rangeResult) {
            System.out.printf("  score=%.1f, name=%s%n", node.score, node.obj);
        }
        
        System.out.println();
    }
    
    /**
     * 排名查询演示
     */
    private static void demonstrateRankQuery(SkipList<String> skipList) {
        System.out.println("3. === 排名查询演示 ===");
        
        SkipList<String> rankList = new SkipList<>();
        
        // 插入排名数据
        rankList.insert(100.0, "第一名");
        rankList.insert(90.0, "第二名");
        rankList.insert(85.0, "第三名");
        rankList.insert(80.0, "第四名");
        rankList.insert(75.0, "第五名");
        
        System.out.println("按排名查询（排名从1开始）：");
        for (int rank = 1; rank <= 5; rank++) {
            SkipListNode<String> node = rankList.getByRank(rank);
            if (node != null) {
                System.out.printf("  排名 %d: score=%.1f, name=%s%n", 
                                rank, node.score, node.obj);
            }
        }
        
        System.out.println();
    }
    
    /**
     * Redis ZSET命令对应演示
     */
    private static void demonstrateRedisZSetOperations() {
        System.out.println("4. === Redis ZSET命令对应演示 ===");
        
        SkipList<String> zset = new SkipList<>();
        
        // ZADD - 添加成员
        System.out.println("ZADD operations:");
        zset.insert(100, "player1");
        zset.insert(200, "player2");
        zset.insert(150, "player3");
        zset.insert(300, "player4");
        zset.insert(120, "player5");
        
        System.out.printf("ZCARD (集合基数): %d%n", zset.getLength());
        
        // ZRANGE - 范围查询（按排名）
        System.out.println("\nZRANGE 0 2 (按排名获取前3名):");
        for (int rank = 1; rank <= 3 && rank <= zset.getLength(); rank++) {
            SkipListNode<String> node = zset.getByRank(rank);
            if (node != null) {
                System.out.printf("  %d) %s (score: %.0f)%n", 
                                rank, node.obj, node.score);
            }
        }
        
        // ZRANGEBYSCORE - 按分值范围查询
        System.out.println("\nZRANGEBYSCORE 100 200 (分值100-200的成员):");
        var scoreRange = zset.getByScoreRange(100, 200);
        for (var node : scoreRange) {
            System.out.printf("  %s (score: %.0f)%n", node.obj, node.score);
        }
        
        // ZSCORE - 获取成员分值
        System.out.println("\nZSCORE player3:");
        SkipListNode<String> player3 = zset.search(150, "player3");
        if (player3 != null) {
            System.out.printf("  player3的分值: %.0f%n", player3.score);
        }
        
        // ZREM - 删除成员
        System.out.println("\nZREM player2:");
        boolean removed = zset.delete(200, "player2");
        System.out.printf("  删除结果: %s%n", removed ? "成功" : "失败");
        System.out.printf("  删除后ZCARD: %d%n", zset.getLength());
        
        System.out.println();
    }
    
    /**
     * 性能测试演示
     */
    private static void demonstratePerformance() {
        System.out.println("5. === 性能测试演示 ===");
        
        SkipList<String> perfList = new SkipList<>();
        int testSize = 10000;
        
        // 插入性能测试
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < testSize; i++) {
            perfList.insert(Math.random() * 1000, "Element" + i);
        }
        long insertTime = System.currentTimeMillis() - startTime;
        
        System.out.printf("插入 %d 个元素耗时: %d ms%n", testSize, insertTime);
        System.out.printf("跳表最终状态 - 长度: %d, 最高层级: %d%n", 
                         perfList.getLength(), perfList.getLevel());
        
        // 查找性能测试
        startTime = System.currentTimeMillis();
        int searchCount = 1000;
        for (int i = 0; i < searchCount; i++) {
            perfList.getByRank((long)(Math.random() * testSize) + 1);
        }
        long searchTime = System.currentTimeMillis() - startTime;
        
        System.out.printf("执行 %d 次排名查找耗时: %d ms%n", searchCount, searchTime);
        
        // 范围查询性能测试
        startTime = System.currentTimeMillis();
        int rangeQueryCount = 100;
        int totalRangeResults = 0;
        for (int i = 0; i < rangeQueryCount; i++) {
            double min = Math.random() * 500;
            double max = min + Math.random() * 200;
            var results = perfList.getByScoreRange(min, max);
            totalRangeResults += results.size();
        }
        long rangeTime = System.currentTimeMillis() - startTime;
        
        System.out.printf("执行 %d 次范围查询耗时: %d ms, 平均每次返回 %.1f 个结果%n", 
                         rangeQueryCount, rangeTime, (double)totalRangeResults / rangeQueryCount);
        
        System.out.println("\n=== 演示结束 ===");
    }
}
