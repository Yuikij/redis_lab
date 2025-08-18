package com.soukon.service;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Redisson 分布式锁详细原理演示服务
 * 
 * Redisson 分布式锁原理：
 * 1. 基于 Lua 脚本保证原子性操作
 * 2. 使用看门狗机制自动续期，防止死锁
 * 3. 支持可重入锁（记录持有线程和重入次数）
 * 4. 支持公平锁、读写锁等多种锁类型
 * 5. 使用 Redis Hash 数据结构存储锁信息
 */
@Slf4j
@Service
public class RedissonDistributedLockService {

    private final RedissonClient redissonClient;
    private final AtomicInteger lockCounter = new AtomicInteger(0);

    public RedissonDistributedLockService(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 基础分布式锁演示
     * 
     * 原理详解：
     * 1. 获取锁时，Redisson 向 Redis 发送 Lua 脚本
     * 2. 脚本检查锁是否存在，不存在则创建，存在则检查是否为同一线程
     * 3. 锁的值为 hash 结构：field 为线程ID，value 为重入次数
     * 4. 设置锁的过期时间，防止死锁
     * 5. 启动看门狗定时任务，自动续期（默认每10秒续期一次）
     * 
     * @param lockKey 锁的键名
     * @param waitTime 等待获取锁的最长时间
     * @param leaseTime 锁的持有时间（-1表示使用看门狗自动续期）
     * @return 是否成功获取锁
     */
    public boolean tryBasicLock(String lockKey, long waitTime, long leaseTime) {
        log.info("🔐 尝试获取分布式锁，锁键：{}，等待时间：{}秒，持有时间：{}秒", 
                lockKey, waitTime, leaseTime == -1 ? "自动续期" : leaseTime);
        
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired = false;
        
        try {
            // 尝试获取锁
            long startTime = System.currentTimeMillis();
            if (leaseTime == -1) {
                // 使用看门狗机制自动续期
                acquired = lock.tryLock(waitTime, TimeUnit.SECONDS);
                log.info("📊 锁获取结果：{}，使用看门狗自动续期机制", acquired ? "成功" : "失败");
            } else {
                // 指定锁的持有时间
                acquired = lock.tryLock(waitTime, leaseTime, TimeUnit.SECONDS);
                log.info("📊 锁获取结果：{}，锁持有时间：{}秒", acquired ? "成功" : "失败", leaseTime);
            }
            
            long acquireTime = System.currentTimeMillis() - startTime;
            log.info("⏱️ 锁获取耗时：{}毫秒", acquireTime);
            
            if (acquired) {
                // 记录锁的详细信息
                String lockValue = getLockInfo(lock);
                log.info("🔒 成功获取锁，锁信息：{}", lockValue);
                log.info("🆔 当前持有锁的线程：{}", Thread.currentThread().getName());
                log.info("🔢 当前锁的重入次数：{}", lock.getHoldCount());
                
                // 模拟业务处理
                simulateBusinessLogic(lockKey);
                
                return true;
            } else {
                log.warn("❌ 获取锁失败，可能原因：锁已被其他线程持有或等待超时");
                return false;
            }
            
        } catch (InterruptedException e) {
            log.error("🚫 获取锁过程中被中断", e);
            Thread.currentThread().interrupt();
            return false;
        } finally {
            // 释放锁
            if (acquired && lock.isHeldByCurrentThread()) {
                try {
                    lock.unlock();
                    log.info("🔓 成功释放锁：{}，当前线程：{}", lockKey, Thread.currentThread().getName());
                } catch (Exception e) {
                    log.error("❌ 释放锁时发生异常：{}", e.getMessage(), e);
                }
            } else if (acquired) {
                log.warn("⚠️ 锁不是由当前线程持有，无法释放");
            }
        }
    }

    /**
     * 可重入锁演示
     * 
     * 原理：
     * 1. 同一线程可以多次获取同一把锁
     * 2. 每次获取锁时，重入次数+1
     * 3. 释放锁时，重入次数-1，直到为0时真正释放锁
     * 4. Redis 中使用 Hash 结构存储：{lockKey: {threadId: holdCount}}
     * 
     * @param lockKey 锁键
     * @param depth 递归深度，模拟重入次数
     */
    public void demonstrateReentrantLock(String lockKey, int depth) {
        log.info("🔄 演示可重入锁，当前递归深度：{}，锁键：{}", depth, lockKey);
        
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            boolean acquired = lock.tryLock(5, TimeUnit.SECONDS);
            if (acquired) {
                log.info("✅ 深度 {} 成功获取可重入锁，重入次数：{}", depth, lock.getHoldCount());
                
                // 递归调用，演示重入特性
                if (depth > 0) {
                    Thread.sleep(100); // 模拟业务处理时间
                    demonstrateReentrantLock(lockKey, depth - 1);
                }
                
                log.info("🔄 深度 {} 开始释放锁，当前重入次数：{}", depth, lock.getHoldCount());
            } else {
                log.warn("❌ 深度 {} 获取锁失败", depth);
            }
        } catch (InterruptedException e) {
            log.error("🚫 可重入锁演示被中断", e);
            Thread.currentThread().interrupt();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.info("🔓 深度 {} 释放锁完成，剩余重入次数：{}", depth, 
                        lock.isLocked() ? lock.getHoldCount() : 0);
            }
        }
    }

    /**
     * 公平锁演示
     * 
     * 原理：
     * 1. 公平锁按照请求锁的顺序分配锁
     * 2. 使用 Redis List 维护等待队列
     * 3. 确保先请求的线程先获得锁，避免饥饿现象
     * 
     * @param lockKey 锁键
     * @param threadName 线程名称（用于标识）
     */
    public boolean tryFairLock(String lockKey, String threadName) {
        log.info("⚖️ {} 尝试获取公平锁：{}", threadName, lockKey);
        
        RLock fairLock = redissonClient.getFairLock(lockKey);
        
        try {
            boolean acquired = fairLock.tryLock(10, 5, TimeUnit.SECONDS);
            if (acquired) {
                log.info("✅ {} 成功获取公平锁，开始执行业务逻辑", threadName);
                
                // 模拟业务处理
                Thread.sleep(2000);
                log.info("💼 {} 业务逻辑执行完成", threadName);
                
                return true;
            } else {
                log.warn("❌ {} 获取公平锁失败", threadName);
                return false;
            }
        } catch (InterruptedException e) {
            log.error("🚫 {} 获取公平锁被中断", threadName, e);
            Thread.currentThread().interrupt();
            return false;
        } finally {
            if (fairLock.isHeldByCurrentThread()) {
                fairLock.unlock();
                log.info("🔓 {} 释放公平锁完成", threadName);
            }
        }
    }

    /**
     * 读写锁演示
     * 
     * 原理：
     * 1. 读锁（共享锁）：多个线程可以同时持有
     * 2. 写锁（排它锁）：只有一个线程可以持有，且与读锁互斥
     * 3. 适用于读多写少的场景，提高并发性能
     * 
     * @param lockKey 锁键
     * @param isWrite 是否为写操作
     * @param operationName 操作名称
     */
    public boolean tryReadWriteLock(String lockKey, boolean isWrite, String operationName) {
        log.info("📚 尝试获取{}锁进行操作：{}，锁键：{}", 
                isWrite ? "写" : "读", operationName, lockKey);
        
        RReadWriteLock readWriteLock = redissonClient.getReadWriteLock(lockKey);
        RLock lock = isWrite ? readWriteLock.writeLock() : readWriteLock.readLock();
        
        try {
            boolean acquired = lock.tryLock(5, 3, TimeUnit.SECONDS);
            if (acquired) {
                log.info("✅ 成功获取{}锁，执行操作：{}", isWrite ? "写" : "读", operationName);
                
                // 模拟读写操作
                if (isWrite) {
                    log.info("✏️ 执行写操作：{}", operationName);
                    Thread.sleep(1500); // 写操作相对较慢
                } else {
                    log.info("👀 执行读操作：{}", operationName);
                    Thread.sleep(500);  // 读操作相对较快
                }
                
                log.info("✔️ {}操作完成：{}", isWrite ? "写" : "读", operationName);
                return true;
            } else {
                log.warn("❌ 获取{}锁失败：{}", isWrite ? "写" : "读", operationName);
                return false;
            }
        } catch (InterruptedException e) {
            log.error("🚫 {}锁操作被中断：{}", isWrite ? "写" : "读", operationName, e);
            Thread.currentThread().interrupt();
            return false;
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.info("🔓 释放{}锁：{}", isWrite ? "写" : "读", operationName);
            }
        }
    }

    /**
     * 演示锁的自动续期机制（看门狗）
     * 
     * 原理：
     * 1. 当不指定锁的持有时间时，Redisson 启动看门狗机制
     * 2. 看门狗定时任务每 internalLockLeaseTime/3 时间执行一次（默认10秒）
     * 3. 如果线程仍持有锁，则将锁的过期时间重置为 internalLockLeaseTime（默认30秒）
     * 4. 这样可以防止业务执行时间过长导致锁自动过期
     * 
     * @param lockKey 锁键
     * @param businessTime 模拟业务执行时间（秒）
     */
    public void demonstrateWatchdog(String lockKey, int businessTime) {
        log.info("🐕 演示看门狗机制，锁键：{}，模拟业务时间：{}秒", lockKey, businessTime);
        
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            // 不指定锁的持有时间，启用看门狗机制
            boolean acquired = lock.tryLock(5, TimeUnit.SECONDS);
            if (acquired) {
                log.info("✅ 获取锁成功，看门狗机制已启动");
                log.info("📋 默认锁过期时间：30秒，看门狗续期间隔：10秒");
                
                // 模拟长时间业务处理
                for (int i = 0; i < businessTime; i++) {
                    Thread.sleep(1000);
                    if (i % 10 == 0 && i > 0) {
                        log.info("🐕 看门狗工作中...业务已执行{}秒，锁仍然有效", i);
                    }
                }
                
                log.info("💼 业务处理完成，总耗时：{}秒", businessTime);
            } else {
                log.warn("❌ 获取锁失败");
            }
        } catch (InterruptedException e) {
            log.error("🚫 看门狗演示被中断", e);
            Thread.currentThread().interrupt();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.info("🔓 释放锁，看门狗停止工作");
            }
        }
    }

    /**
     * 模拟业务逻辑
     */
    private void simulateBusinessLogic(String lockKey) {
        try {
            int businessId = lockCounter.incrementAndGet();
            log.info("💼 开始执行业务逻辑，业务ID：{}，锁键：{}", businessId, lockKey);
            
            // 模拟业务处理时间
            Thread.sleep(1000 + (int)(Math.random() * 2000));
            
            log.info("✅ 业务逻辑执行完成，业务ID：{}", businessId);
        } catch (InterruptedException e) {
            log.error("🚫 业务逻辑执行被中断", e);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 获取锁的详细信息（用于调试）
     */
    private String getLockInfo(RLock lock) {
        try {
            return String.format("锁状态：%s，是否被当前线程持有：%s，重入次数：%d，剩余时间：%d毫秒",
                    lock.isLocked() ? "已锁定" : "未锁定",
                    lock.isHeldByCurrentThread() ? "是" : "否",
                    lock.getHoldCount(),
                    lock.remainTimeToLive());
        } catch (Exception e) {
            return "无法获取锁信息：" + e.getMessage();
        }
    }
}
