package com.soukon.service;

import com.soukon.datastructure.SkipList;
import com.soukon.datastructure.SkipListNode;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 跳表演示服务
 * 
 * 演示Redis跳表的各种操作，包括：
 * 1. 基本的增删改查操作
 * 2. 范围查询
 * 3. 排名查询
 * 4. 性能测试
 */
@Service
public class SkipListDemoService {
    
    /**
     * 演示跳表基本操作
     */
    public void demonstrateBasicOperations() {
        System.out.println("=== Redis跳表基本操作演示 ===");
        
        SkipList<String> skipList = new SkipList<>();
        
        // 插入操作
        System.out.println("1. 插入操作：");
        skipList.insert(1.0, "Alice");
        skipList.insert(2.5, "Bob");
        skipList.insert(1.8, "Charlie");
        skipList.insert(3.2, "David");
        skipList.insert(0.5, "Eve");
        skipList.insert(2.1, "Frank");
        
        System.out.printf("插入6个元素后，跳表长度: %d, 最高层级: %d%n", 
                         skipList.getLength(), skipList.getLevel());
        skipList.printStructure();
        
        // 查找操作
        System.out.println("2. 查找操作：");
        SkipListNode<String> found = skipList.search(2.5, "Bob");
        System.out.println("查找 score=2.5, obj=Bob: " + 
                          (found != null ? "找到 " + found : "未找到"));
        
        found = skipList.search(10.0, "NotExist");
        System.out.println("查找 score=10.0, obj=NotExist: " + 
                          (found != null ? "找到 " + found : "未找到"));
        
        // 删除操作
        System.out.println("\n3. 删除操作：");
        boolean deleted = skipList.delete(1.8, "Charlie");
        System.out.println("删除 score=1.8, obj=Charlie: " + 
                          (deleted ? "成功" : "失败"));
        System.out.printf("删除后跳表长度: %d%n", skipList.getLength());
        
        System.out.println();
    }
    
    /**
     * 演示范围查询
     */
    public void demonstrateRangeQuery() {
        System.out.println("=== 跳表范围查询演示 ===");
        
        SkipList<String> skipList = new SkipList<>();
        
        // 插入测试数据
        String[] names = {"Alice", "Bob", "Charlie", "David", "Eve", "Frank", "Grace", "Henry"};
        double[] scores = {1.0, 2.5, 1.8, 3.2, 0.5, 2.1, 4.0, 3.8};
        
        for (int i = 0; i < names.length; i++) {
            skipList.insert(scores[i], names[i]);
        }
        
        System.out.println("插入的数据：");
        for (int i = 0; i < names.length; i++) {
            System.out.printf("score=%.1f, name=%s%n", scores[i], names[i]);
        }
        
        // 范围查询
        System.out.println("\n范围查询 [2.0, 3.5]：");
        List<SkipListNode<String>> rangeResult = skipList.getByScoreRange(2.0, 3.5);
        for (SkipListNode<String> node : rangeResult) {
            System.out.printf("score=%.1f, name=%s%n", node.score, node.obj);
        }
        
        System.out.println();
    }
    
    /**
     * 演示排名查询
     */
    public void demonstrateRankQuery() {
        System.out.println("=== 跳表排名查询演示 ===");
        
        SkipList<String> skipList = new SkipList<>();
        
        // 插入有序数据
        skipList.insert(10.0, "第一名");
        skipList.insert(20.0, "第二名");
        skipList.insert(30.0, "第三名");
        skipList.insert(40.0, "第四名");
        skipList.insert(50.0, "第五名");
        
        System.out.println("按排名查询（排名从1开始）：");
        for (int rank = 1; rank <= 5; rank++) {
            SkipListNode<String> node = skipList.getByRank(rank);
            if (node != null) {
                System.out.printf("排名 %d: score=%.1f, name=%s%n", 
                                rank, node.score, node.obj);
            }
        }
        
        // 查询边界情况
        System.out.println("\n边界查询：");
        SkipListNode<String> first = skipList.getFirst();
        SkipListNode<String> last = skipList.getLast();
        System.out.println("第一个节点: " + (first != null ? first : "null"));
        System.out.println("最后一个节点: " + (last != null ? last : "null"));
        
        System.out.println();
    }
    
    /**
     * 演示跳表性能
     */
    public void demonstratePerformance() {
        System.out.println("=== 跳表性能测试演示 ===");
        
        SkipList<String> skipList = new SkipList<>();
        int testSize = 10000;
        
        // 插入性能测试
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < testSize; i++) {
            skipList.insert(Math.random() * 1000, "Element" + i);
        }
        long insertTime = System.currentTimeMillis() - startTime;
        
        System.out.printf("插入 %d 个元素耗时: %d ms%n", testSize, insertTime);
        System.out.printf("跳表最终状态 - 长度: %d, 最高层级: %d%n", 
                         skipList.getLength(), skipList.getLevel());
        
        // 查找性能测试
        startTime = System.currentTimeMillis();
        int searchCount = 1000;
        int foundCount = 0;
        for (int i = 0; i < searchCount; i++) {
            double searchScore = Math.random() * 1000;
            String searchObj = "Element" + (int)(Math.random() * testSize);
            if (skipList.search(searchScore, searchObj) != null) {
                foundCount++;
            }
        }
        long searchTime = System.currentTimeMillis() - startTime;
        
        System.out.printf("执行 %d 次查找操作耗时: %d ms, 找到 %d 个%n", 
                         searchCount, searchTime, foundCount);
        
        // 范围查询性能测试
        startTime = System.currentTimeMillis();
        int rangeQueryCount = 100;
        int totalRangeResults = 0;
        for (int i = 0; i < rangeQueryCount; i++) {
            double min = Math.random() * 500;
            double max = min + Math.random() * 200;
            List<SkipListNode<String>> results = skipList.getByScoreRange(min, max);
            totalRangeResults += results.size();
        }
        long rangeTime = System.currentTimeMillis() - startTime;
        
        System.out.printf("执行 %d 次范围查询耗时: %d ms, 平均每次返回 %.1f 个结果%n", 
                         rangeQueryCount, rangeTime, (double)totalRangeResults / rangeQueryCount);
        
        System.out.println();
    }
    
    /**
     * 演示Redis ZSET命令对应的跳表操作
     */
    public void demonstrateRedisZSetOperations() {
        System.out.println("=== Redis ZSET命令对应的跳表操作演示 ===");
        
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
        List<SkipListNode<String>> scoreRange = zset.getByScoreRange(100, 200);
        for (SkipListNode<String> node : scoreRange) {
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
     * 运行所有演示
     */
    public void runAllDemos() {
        demonstrateBasicOperations();
        demonstrateRangeQuery();
        demonstrateRankQuery();
        demonstratePerformance();
        demonstrateRedisZSetOperations();
    }
}

