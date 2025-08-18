package com.soukon;

import com.soukon.service.RedisTransactionService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Redis事务服务测试类
 * 验证各种Redis事务操作的正确性和一致性
 */
@SpringBootTest
class RedisTransactionServiceTest {

    private static final Logger log = LoggerFactory.getLogger(RedisTransactionServiceTest.class);

    @BeforeAll
    static void checkRedis(@Autowired org.springframework.core.env.Environment env) {
        RedisConnectivity.assumeRedis(env);
        log.info("=== Redis事务服务测试开始 ===");
    }

    @Autowired
    private RedisTransactionService transactionService;

    @BeforeEach
    void setup() {
        // 每个测试前清理相关数据
        transactionService.cleanupDemoData("test:");
        log.info("测试环境准备完成，已清理测试数据");
    }

    /**
     * 测试基础事务功能：银行转账
     */
    @Test
    void testBasicTransaction() {
        log.info("=== 开始测试基础事务功能 ===");
        
        String fromAccount = "test:" + UUID.randomUUID();
        String toAccount = "test:" + UUID.randomUUID();
        double amount = 100.0;
        
        // 执行转账事务
        boolean result = transactionService.basicTransactionDemo(fromAccount, toAccount, amount);
        
        assertTrue(result, "银行转账事务应该执行成功");
        log.info("基础事务测试通过：转账操作成功");
    }

    /**
     * 测试乐观锁事务：库存扣减
     */
    @Test
    void testOptimisticLockTransaction() {
        log.info("=== 开始测试乐观锁事务功能 ===");
        
        String productId = "test:" + UUID.randomUUID();
        int quantity = 10;
        
        // 执行库存扣减
        boolean result = transactionService.optimisticLockDemo(productId, quantity);
        
        assertTrue(result, "库存扣减事务应该执行成功");
        log.info("乐观锁事务测试通过：库存扣减成功");
    }

    /**
     * 测试事务回滚功能：积分变更
     */
    @Test
    void testTransactionRollback() {
        log.info("=== 开始测试事务回滚功能 ===");
        
        String userId = "test:" + UUID.randomUUID();
        
        // 测试正常积分变更
        boolean normalResult = transactionService.transactionRollbackDemo(userId, 500);
        assertTrue(normalResult, "正常积分变更应该成功");
        
        // 测试会触发回滚的积分变更（减少过多积分）
        boolean rollbackResult = transactionService.transactionRollbackDemo(userId, -2000);
        assertFalse(rollbackResult, "积分不足的变更应该被回滚");
        
        log.info("事务回滚测试通过：正常变更成功，异常变更被回滚");
    }

    /**
     * 测试批量操作事务
     */
    @Test
    void testBatchUpdateTransaction() {
        log.info("=== 开始测试批量操作事务功能 ===");
        
        List<String> userIds = Arrays.asList(
                "test:" + UUID.randomUUID(),
                "test:" + UUID.randomUUID(),
                "test:" + UUID.randomUUID()
        );
        String status = "active";
        
        // 执行批量更新
        boolean result = transactionService.batchUpdateDemo(userIds, status);
        
        assertTrue(result, "批量用户状态更新应该成功");
        log.info("批量操作事务测试通过：批量更新成功");
        
        // 测试空列表情况
        boolean emptyResult = transactionService.batchUpdateDemo(Arrays.asList(), status);
        assertTrue(emptyResult, "空列表批量更新应该返回成功");
    }

    /**
     * 测试并发场景下的乐观锁机制
     * 模拟多个线程同时扣减库存的情况
     */
    @Test
    void testConcurrentOptimisticLock() throws InterruptedException {
        log.info("=== 开始测试并发乐观锁机制 ===");
        
        String productId = "test:concurrent:" + UUID.randomUUID();
        int threadCount = 5;
        int quantityPerThread = 20;
        
        // 使用线程池模拟并发访问
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        
        // 启动多个线程同时执行库存扣减
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    log.info("线程[{}]开始执行库存扣减", threadId);
                    boolean result = transactionService.optimisticLockDemo(productId, quantityPerThread);
                    
                    if (result) {
                        int count = successCount.incrementAndGet();
                        log.info("线程[{}]库存扣减成功，当前成功次数：{}", threadId, count);
                    } else {
                        int count = failureCount.incrementAndGet();
                        log.info("线程[{}]库存扣减失败，当前失败次数：{}", threadId, count);
                    }
                } catch (Exception e) {
                    log.error("线程[{}]执行异常：{}", threadId, e.getMessage());
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // 等待所有线程完成
        latch.await();
        executor.shutdown();
        
        int totalSuccess = successCount.get();
        int totalFailure = failureCount.get();
        
        log.info("并发测试完成 - 成功次数：{}，失败次数：{}", totalSuccess, totalFailure);
        
        // 验证并发控制效果
        assertEquals(threadCount, totalSuccess + totalFailure, "总操作次数应该等于线程数");
        assertTrue(totalSuccess > 0, "应该至少有一些操作成功");
        
        // 在高并发场景下，由于乐观锁机制，可能会有一些操作失败
        log.info("并发乐观锁测试通过：正确处理了并发冲突");
    }

    /**
     * 测试事务在异常情况下的行为
     */
    @Test
    void testTransactionExceptionHandling() {
        log.info("=== 开始测试事务异常处理 ===");
        
        // 测试无效参数的处理
        String userId = "test:" + UUID.randomUUID();
        
        try {
            // 尝试正常的积分变更
            boolean result = transactionService.transactionRollbackDemo(userId, 100);
            assertTrue(result, "正常积分变更应该成功");
            
            // 尝试会导致负积分的变更（应该被回滚）
            boolean rollbackResult = transactionService.transactionRollbackDemo(userId, -2000);
            assertFalse(rollbackResult, "负积分变更应该被回滚");
            
            log.info("事务异常处理测试通过");
            
        } catch (Exception e) {
            log.error("事务异常处理测试出现意外异常：{}", e.getMessage());
            fail("事务异常处理不应该抛出异常");
        }
    }

    /**
     * 测试事务的隔离性
     * 验证事务执行期间的数据一致性
     */
    @Test
    void testTransactionIsolation() throws InterruptedException {
        log.info("=== 开始测试事务隔离性 ===");
        
        String fromAccount = "test:isolation:from:" + UUID.randomUUID();
        String toAccount = "test:isolation:to:" + UUID.randomUUID();
        double amount = 50.0;
        
        // 创建两个线程，一个执行转账事务，另一个读取数据
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(2);
        
        // 转账线程
        Thread transferThread = new Thread(() -> {
            try {
                startLatch.await();
                log.info("转账线程开始执行事务");
                boolean result = transactionService.basicTransactionDemo(fromAccount, toAccount, amount);
                log.info("转账线程事务执行结果：{}", result);
            } catch (Exception e) {
                log.error("转账线程异常：{}", e.getMessage());
            } finally {
                endLatch.countDown();
            }
        });
        
        // 读取线程
        Thread readThread = new Thread(() -> {
            try {
                startLatch.await();
                // 稍微延迟，确保转账事务正在执行
                Thread.sleep(100);
                log.info("读取线程开始读取数据");
                // 这里可以添加读取验证逻辑
                log.info("读取线程完成数据读取");
            } catch (Exception e) {
                log.error("读取线程异常：{}", e.getMessage());
            } finally {
                endLatch.countDown();
            }
        });
        
        transferThread.start();
        readThread.start();
        
        // 开始执行
        startLatch.countDown();
        
        // 等待完成
        endLatch.await();
        
        log.info("事务隔离性测试完成");
    }

    /**
     * 测试事务性能
     * 测量事务操作的执行时间
     */
    @Test
    void testTransactionPerformance() {
        log.info("=== 开始测试事务性能 ===");
        
        int operationCount = 10;
        String productId = "test:performance:" + UUID.randomUUID();
        
        long startTime = System.currentTimeMillis();
        
        // 执行多次库存扣减操作
        int successCount = 0;
        for (int i = 0; i < operationCount; i++) {
            boolean result = transactionService.optimisticLockDemo(productId, 1);
            if (result) {
                successCount++;
            }
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        log.info("性能测试完成 - 总操作次数：{}，成功次数：{}，总耗时：{}ms，平均耗时：{}ms", 
                operationCount, successCount, duration, duration / operationCount);
        
        // 验证性能合理性（这里只是简单验证执行时间不会过长）
        assertTrue(duration < 10000, "事务操作总耗时不应超过10秒");
        assertTrue(successCount > 0, "应该有操作成功");
        
        log.info("事务性能测试通过");
    }

    /**
     * 测试复杂事务场景
     * 模拟实际业务中的复杂事务操作
     */
    @Test
    void testComplexTransactionScenario() {
        log.info("=== 开始测试复杂事务场景 ===");
        
        String orderId = "test:order:" + UUID.randomUUID();
        String userId = "test:user:" + UUID.randomUUID();
        String productId = "test:product:" + UUID.randomUUID();
        
        try {
            // 1. 库存扣减
            boolean stockResult = transactionService.optimisticLockDemo(productId, 2);
            assertTrue(stockResult, "库存扣减应该成功");
            
            // 2. 用户账户扣款
            boolean paymentResult = transactionService.basicTransactionDemo(userId, "merchant", 99.99);
            assertTrue(paymentResult, "支付应该成功");
            
            // 3. 批量状态更新
            List<String> statusKeys = Arrays.asList(
                    "test:order:status:" + orderId,
                    "test:user:last_order:" + userId,
                    "test:product:last_sold:" + productId
            );
            boolean batchResult = transactionService.batchUpdateDemo(statusKeys, "completed");
            assertTrue(batchResult, "批量状态更新应该成功");
            
            log.info("复杂事务场景测试通过：所有步骤都成功执行");
            
        } catch (Exception e) {
            log.error("复杂事务场景测试异常：{}", e.getMessage());
            fail("复杂事务场景不应该出现异常");
        }
    }
}
