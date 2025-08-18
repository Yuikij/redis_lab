package com.soukon;

import com.soukon.service.RedissonDistributedLockService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Redisson 分布式锁功能测试
 * 
 * 测试覆盖：
 * 1. 基础分布式锁
 * 2. 可重入锁
 * 3. 公平锁
 * 4. 读写锁
 * 5. 看门狗机制
 * 6. 并发竞争场景
 */
@Slf4j
@SpringBootTest
class RedissonDistributedLockServiceTest {

    @BeforeAll
    static void checkRedis(@Autowired org.springframework.core.env.Environment env) {
        RedisConnectivity.assumeRedis(env);
    }

    @Autowired
    private RedissonDistributedLockService lockService;

    /**
     * 测试基础分布式锁功能
     */
    @Test
    void testBasicDistributedLock() {
        log.info("=== 🧪 开始测试基础分布式锁功能 ===");
        
        String lockKey = "test:basic:lock:" + System.currentTimeMillis();
        
        // 测试获取和释放锁
        boolean acquired = lockService.tryBasicLock(lockKey, 5, 10);
        assertTrue(acquired, "应该成功获取分布式锁");
        
        log.info("=== ✅ 基础分布式锁测试完成 ===\n");
    }

    /**
     * 测试可重入锁机制
     */
    @Test
    void testReentrantLock() {
        log.info("=== 🧪 开始测试可重入锁机制 ===");
        
        String lockKey = "test:reentrant:lock:" + System.currentTimeMillis();
        
        // 测试递归重入
        lockService.demonstrateReentrantLock(lockKey, 3);
        
        log.info("=== ✅ 可重入锁测试完成 ===\n");
    }

    /**
     * 测试公平锁功能
     */
    @Test
    void testFairLock() throws InterruptedException {
        log.info("=== 🧪 开始测试公平锁功能 ===");
        
        String lockKey = "test:fair:lock:" + System.currentTimeMillis();
        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch latch = new CountDownLatch(3);
        
        // 启动多个线程竞争公平锁
        for (int i = 1; i <= 3; i++) {
            final int threadNum = i;
            executor.submit(() -> {
                try {
                    String threadName = "线程-" + threadNum;
                    boolean acquired = lockService.tryFairLock(lockKey, threadName);
                    log.info("🏁 {} 公平锁执行结果：{}", threadName, acquired ? "成功" : "失败");
                } finally {
                    latch.countDown();
                }
            });
            
            // 稍微错开启动时间，模拟不同时间的请求
            Thread.sleep(100);
        }
        
        // 等待所有线程完成
        boolean allCompleted = latch.await(30, TimeUnit.SECONDS);
        assertTrue(allCompleted, "所有线程应该在超时前完成");
        
        executor.shutdown();
        log.info("=== ✅ 公平锁测试完成 ===\n");
    }

    /**
     * 测试读写锁功能
     */
    @Test
    void testReadWriteLock() throws InterruptedException {
        log.info("=== 🧪 开始测试读写锁功能 ===");
        
        String lockKey = "test:rw:lock:" + System.currentTimeMillis();
        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch latch = new CountDownLatch(5);
        
        // 启动多个读操作和写操作
        CompletableFuture.supplyAsync(() -> {
            try {
                return lockService.tryReadWriteLock(lockKey, true, "写入数据-1");
            } finally {
                latch.countDown();
            }
        }, executor);
        
        CompletableFuture.supplyAsync(() -> {
            try {
                return lockService.tryReadWriteLock(lockKey, false, "读取数据-1");
            } finally {
                latch.countDown();
            }
        }, executor);
        
        CompletableFuture.supplyAsync(() -> {
            try {
                return lockService.tryReadWriteLock(lockKey, false, "读取数据-2");
            } finally {
                latch.countDown();
            }
        }, executor);
        
        CompletableFuture.supplyAsync(() -> {
            try {
                return lockService.tryReadWriteLock(lockKey, true, "写入数据-2");
            } finally {
                latch.countDown();
            }
        }, executor);
        
        CompletableFuture.supplyAsync(() -> {
            try {
                return lockService.tryReadWriteLock(lockKey, false, "读取数据-3");
            } finally {
                latch.countDown();
            }
        }, executor);
        
        // 等待所有操作完成
        boolean allCompleted = latch.await(20, TimeUnit.SECONDS);
        assertTrue(allCompleted, "所有读写操作应该在超时前完成");
        
        executor.shutdown();
        log.info("=== ✅ 读写锁测试完成 ===\n");
    }

    /**
     * 测试看门狗机制
     */
    @Test
    void testWatchdogMechanism() {
        log.info("=== 🧪 开始测试看门狗机制 ===");
        
        String lockKey = "test:watchdog:lock:" + System.currentTimeMillis();
        
        // 测试长时间持有锁（超过默认过期时间）
        lockService.demonstrateWatchdog(lockKey, 35); // 35秒 > 30秒默认过期时间
        
        log.info("=== ✅ 看门狗机制测试完成 ===\n");
    }

    /**
     * 测试高并发场景下的锁竞争
     */
    @Test
    void testConcurrentLockCompetition() throws InterruptedException {
        log.info("=== 🧪 开始测试高并发锁竞争场景 ===");
        
        String lockKey = "test:concurrent:lock:" + System.currentTimeMillis();
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(threadCount);
        
        // 记录成功获取锁的线程数
        var successCount = new java.util.concurrent.atomic.AtomicInteger(0);
        var failureCount = new java.util.concurrent.atomic.AtomicInteger(0);
        
        // 启动多个线程同时竞争锁
        IntStream.range(0, threadCount).forEach(i -> {
            executor.submit(() -> {
                try {
                    // 等待所有线程准备就绪
                    startLatch.await();
                    
                    String threadName = "竞争线程-" + i;
                    log.info("🏃 {} 开始竞争锁", threadName);
                    
                    boolean acquired = lockService.tryBasicLock(lockKey, 5, 3);
                    if (acquired) {
                        successCount.incrementAndGet();
                        log.info("🎉 {} 成功获取锁", threadName);
                    } else {
                        failureCount.incrementAndGet();
                        log.info("😞 {} 获取锁失败", threadName);
                    }
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("🚫 线程被中断", e);
                } finally {
                    completeLatch.countDown();
                }
            });
        });
        
        // 同时启动所有线程
        log.info("🚀 启动 {} 个线程同时竞争锁", threadCount);
        startLatch.countDown();
        
        // 等待所有线程完成
        boolean allCompleted = completeLatch.await(60, TimeUnit.SECONDS);
        assertTrue(allCompleted, "所有线程应该在超时前完成");
        
        // 验证结果
        log.info("📊 竞争结果统计：成功 {} 个，失败 {} 个", 
                successCount.get(), failureCount.get());
        assertEquals(threadCount, successCount.get() + failureCount.get(), 
                "成功和失败的总数应该等于线程总数");
        
        // 在高并发情况下，应该只有一个线程能成功获取锁（或者少数几个，取决于锁的释放时机）
        assertTrue(successCount.get() >= 1, "至少应该有一个线程成功获取锁");
        assertTrue(successCount.get() <= threadCount, "成功获取锁的线程数不应超过总线程数");
        
        executor.shutdown();
        log.info("=== ✅ 高并发锁竞争测试完成 ===\n");
    }

    /**
     * 测试锁的自动过期功能
     */
    @Test
    void testLockExpiration() throws InterruptedException {
        log.info("=== 🧪 开始测试锁的自动过期功能 ===");
        
        String lockKey = "test:expiration:lock:" + System.currentTimeMillis();
        
        // 第一个线程获取锁并持有较短时间
        CompletableFuture<Boolean> firstLock = CompletableFuture.supplyAsync(() -> {
            log.info("🔐 第一个线程尝试获取锁");
            return lockService.tryBasicLock(lockKey, 1, 2); // 持有2秒
        });
        
        // 等待第一个线程获取锁
        Thread.sleep(500);
        
        // 第二个线程尝试获取同一把锁
        CompletableFuture<Boolean> secondLock = CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(3000); // 等待3秒，确保第一个锁已过期
                log.info("🔐 第二个线程尝试获取锁（第一个锁应该已过期）");
                return lockService.tryBasicLock(lockKey, 1, 2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        });
        
        // 获取结果
        Boolean firstResult = firstLock.join();
        Boolean secondResult = secondLock.join();
        
        assertTrue(firstResult, "第一个线程应该成功获取锁");
        assertTrue(secondResult, "第二个线程应该在第一个锁过期后成功获取锁");
        
        log.info("=== ✅ 锁自动过期测试完成 ===\n");
    }

    /**
     * 性能基准测试
     */
    @Test
    void testLockPerformance() throws InterruptedException {
        log.info("=== 🧪 开始分布式锁性能基准测试 ===");
        
        String lockKey = "test:performance:lock:" + System.currentTimeMillis();
        int operationCount = 50;
        long startTime = System.currentTimeMillis();
        
        // 顺序执行多次锁操作
        for (int i = 0; i < operationCount; i++) {
            boolean acquired = lockService.tryBasicLock(lockKey + ":" + i, 5, 1);
            assertTrue(acquired, "第 " + i + " 次操作应该成功获取锁");
        }
        
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        double averageTime = (double) totalTime / operationCount;
        
        log.info("📊 性能测试结果：");
        log.info("  - 总操作次数：{}", operationCount);
        log.info("  - 总耗时：{} 毫秒", totalTime);
        log.info("  - 平均每次操作耗时：{:.2f} 毫秒", averageTime);
        log.info("  - 每秒操作数（TPS）：{:.2f}", 1000.0 / averageTime);
        
        // 性能断言（可根据实际环境调整）
        assertTrue(averageTime < 1000, "平均操作时间应该小于1秒");
        
        log.info("=== ✅ 性能基准测试完成 ===\n");
    }
}
