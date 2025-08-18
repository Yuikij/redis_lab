# Redisson 分布式锁原理详解

## 📋 目录
- [概述](#概述)
- [核心原理](#核心原理)
- [锁类型详解](#锁类型详解)
- [代码示例](#代码示例)
- [API 接口](#api-接口)
- [性能优化](#性能优化)
- [最佳实践](#最佳实践)

## 概述

Redisson 是一个在 Redis 基础上构建的 Java 分布式并发库，提供了丰富的分布式对象和服务。其分布式锁是基于 Redis 实现的高性能、可靠的分布式同步原语。

### 🎯 主要特性
- **原子性操作**：基于 Lua 脚本保证操作的原子性
- **看门狗机制**：自动续期，防止死锁
- **可重入性**：同一线程可多次获取同一把锁
- **多种锁类型**：支持公平锁、读写锁、信号量等
- **高性能**：基于 Redis 的内存存储，响应速度快

## 核心原理

### 🔧 1. 基础实现原理

Redisson 分布式锁的核心实现基于以下几个关键技术：

#### Redis 数据结构
```redis
# 锁的存储结构（Hash）
HASH myLock {
    "8743c9c0-0795-4907-87fd-6c719a6b4586:1" : 1
}
# Key: 锁名
# Field: 客户端ID + 线程ID
# Value: 重入次数
```

#### Lua 脚本保证原子性
```lua
-- 获取锁的 Lua 脚本（简化版）
if (redis.call('exists', KEYS[1]) == 0) then
    redis.call('hset', KEYS[1], ARGV[2], 1);
    redis.call('pexpire', KEYS[1], ARGV[1]);
    return nil;
end;
if (redis.call('hexists', KEYS[1], ARGV[2]) == 1) then
    redis.call('hincrby', KEYS[1], ARGV[2], 1);
    redis.call('pexpire', KEYS[1], ARGV[1]);
    return nil;
end;
return redis.call('pttl', KEYS[1]);
```

### 🐕 2. 看门狗机制（Watchdog）

看门狗是 Redisson 的核心特性，解决了锁自动过期的问题：

```java
// 伪代码
class Watchdog {
    // 默认锁过期时间：30秒
    private static final long DEFAULT_LEASE_TIME = 30000;
    
    // 续期间隔：过期时间的 1/3，即 10秒
    private static final long RENEWAL_INTERVAL = DEFAULT_LEASE_TIME / 3;
    
    public void startWatchdog(String lockKey, String lockValue) {
        ScheduledFuture<?> renewalTask = scheduler.schedule(() -> {
            if (isLockStillHeld(lockKey, lockValue)) {
                // 续期锁，重置过期时间
                redis.pexpire(lockKey, DEFAULT_LEASE_TIME);
                // 递归调用，继续监控
                startWatchdog(lockKey, lockValue);
            }
        }, RENEWAL_INTERVAL, TimeUnit.MILLISECONDS);
    }
}
```

### 🔄 3. 可重入机制

可重入锁允许同一线程多次获取同一把锁：

```java
// 重入计数实现
public boolean tryLock() {
    String threadId = getThreadId();
    Long ttl = tryAcquire(threadId);
    
    if (ttl == null) {
        // 获取锁成功
        return true;
    }
    
    // 锁被其他线程持有
    return false;
}

private Long tryAcquire(String threadId) {
    return redis.eval(
        // 如果锁不存在，创建锁
        "if (redis.call('exists', KEYS[1]) == 0) then " +
        "    redis.call('hset', KEYS[1], ARGV[2], 1); " +
        "    redis.call('pexpire', KEYS[1], ARGV[1]); " +
        "    return nil; " +
        "end; " +
        // 如果是同一线程，增加重入次数
        "if (redis.call('hexists', KEYS[1], ARGV[2]) == 1) then " +
        "    redis.call('hincrby', KEYS[1], ARGV[2], 1); " +
        "    redis.call('pexpire', KEYS[1], ARGV[1]); " +
        "    return nil; " +
        "end; " +
        // 返回锁的剩余时间
        "return redis.call('pttl', KEYS[1]);",
        Collections.singletonList(lockKey),
        leaseTime, threadId);
}
```

## 锁类型详解

### 🔒 1. 基础分布式锁（RLock）

**特点**：
- 互斥锁，同时只能有一个线程持有
- 支持可重入
- 支持自动续期

**使用场景**：
- 防止重复提交
- 确保数据一致性
- 控制资源访问

### ⚖️ 2. 公平锁（Fair Lock）

**特点**：
- 按照请求顺序分配锁
- 防止饥饿现象
- 基于 Redis List 实现队列

**实现原理**：
```redis
# 公平锁的队列结构
LIST fairLockQueue [
    "client1:thread1",
    "client2:thread1", 
    "client1:thread2"
]
```

### 📚 3. 读写锁（ReadWriteLock）

**特点**：
- 读锁（共享锁）：多个线程可同时持有
- 写锁（排它锁）：与读锁和写锁互斥
- 适合读多写少场景

**实现原理**：
```java
// 读写锁状态表示
// 高16位：读锁计数
// 低16位：写锁重入次数
int state = 0x00010001; // 1个读锁 + 1个写锁重入
```

### 🔢 4. 信号量（Semaphore）

**特点**：
- 控制同时访问资源的线程数
- 支持公平和非公平模式
- 基于 Redis String 实现计数

## 代码示例

### 基础使用

```java
@Service
public class OrderService {
    
    @Autowired
    private RedissonClient redissonClient;
    
    public void processOrder(String orderId) {
        String lockKey = "order:lock:" + orderId;
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            // 尝试获取锁，等待时间5秒，锁持有时间10秒
            if (lock.tryLock(5, 10, TimeUnit.SECONDS)) {
                // 处理订单逻辑
                processOrderLogic(orderId);
            } else {
                throw new RuntimeException("获取订单锁失败");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("获取锁被中断", e);
        } finally {
            // 释放锁
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
```

### 看门狗机制

```java
public void longRunningTask() {
    RLock lock = redissonClient.getLock("task:lock");
    
    try {
        // 不指定锁持有时间，启用看门狗
        if (lock.tryLock(5, TimeUnit.SECONDS)) {
            // 执行长时间任务（可能超过30秒）
            performLongTask();
        }
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    } finally {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
```

### 读写锁示例

```java
@Service
public class CacheService {
    
    @Autowired
    private RedissonClient redissonClient;
    
    public String readData(String key) {
        RReadWriteLock rwLock = redissonClient.getReadWriteLock("data:lock:" + key);
        RLock readLock = rwLock.readLock();
        
        try {
            readLock.lock(10, TimeUnit.SECONDS);
            // 读取数据
            return doReadData(key);
        } finally {
            if (readLock.isHeldByCurrentThread()) {
                readLock.unlock();
            }
        }
    }
    
    public void writeData(String key, String value) {
        RReadWriteLock rwLock = redissonClient.getReadWriteLock("data:lock:" + key);
        RLock writeLock = rwLock.writeLock();
        
        try {
            writeLock.lock(10, TimeUnit.SECONDS);
            // 写入数据
            doWriteData(key, value);
        } finally {
            if (writeLock.isHeldByCurrentThread()) {
                writeLock.unlock();
            }
        }
    }
}
```

## API 接口

本项目提供了 REST API 来演示各种分布式锁功能：

### 基础锁
```bash
GET /api/distributed-lock/basic?lockKey=myLock&waitTime=5&leaseTime=10
```

### 可重入锁
```bash
GET /api/distributed-lock/reentrant?lockKey=myLock&depth=3
```

### 公平锁
```bash
GET /api/distributed-lock/fair?lockKey=myLock&threadName=client1
```

### 读写锁
```bash
# 读操作
GET /api/distributed-lock/read-write?lockKey=myLock&operation=read&operationName=查询用户

# 写操作
GET /api/distributed-lock/read-write?lockKey=myLock&operation=write&operationName=更新用户
```

### 看门狗演示
```bash
GET /api/distributed-lock/watchdog?lockKey=myLock&businessTime=35
```

### 并发竞争
```bash
POST /api/distributed-lock/concurrent?lockKey=myLock&threadCount=5
```

## 性能优化

### 1. 连接池配置

```yaml
# application.yml
spring:
  redis:
    redisson:
      config: |
        singleServerConfig:
          connectionPoolSize: 10
          connectionMinimumIdleSize: 5
          subscriptionConnectionPoolSize: 5
```

### 2. 锁粒度优化

```java
// 粗粒度锁（不推荐）
RLock lock = redissonClient.getLock("global:lock");

// 细粒度锁（推荐）
RLock lock = redissonClient.getLock("user:lock:" + userId);
```

### 3. 超时时间调优

```java
// 根据业务场景调整超时时间
boolean acquired = lock.tryLock(
    waitTime,    // 等待时间：根据并发量调整
    leaseTime,   // 持有时间：根据业务执行时间调整
    TimeUnit.SECONDS
);
```

## 最佳实践

### ✅ 推荐做法

1. **总是在 finally 中释放锁**
```java
try {
    if (lock.tryLock()) {
        // 业务逻辑
    }
} finally {
    if (lock.isHeldByCurrentThread()) {
        lock.unlock();
    }
}
```

2. **使用合适的锁粒度**
```java
// 针对具体资源加锁
String lockKey = "resource:lock:" + resourceId;
```

3. **设置合理的超时时间**
```java
// 等待时间不宜过长，避免用户等待
// 持有时间应略大于业务执行时间
lock.tryLock(3, 10, TimeUnit.SECONDS);
```

4. **使用看门狗机制处理长时间任务**
```java
// 不确定任务执行时间时，使用看门狗
lock.tryLock(5, TimeUnit.SECONDS); // 不指定持有时间
```

### ❌ 避免的做法

1. **不要忘记释放锁**
2. **不要在锁内部执行耗时的网络请求**
3. **不要使用过于宽泛的锁名**
4. **不要在事务外部获取锁，在事务内部使用**

### 🔧 故障处理

1. **连接超时**
```java
// 配置连接重试
config.setRetryAttempts(3);
config.setRetryInterval(1500);
```

2. **锁争用激烈**
```java
// 使用公平锁或信号量
RLock fairLock = redissonClient.getFairLock(lockKey);
```

3. **死锁检测**
```java
// 监控锁的持有时间
long remainTime = lock.remainTimeToLive();
if (remainTime > maxAllowedTime) {
    // 记录警告日志或采取措施
}
```

## 🧪 测试验证

运行测试用例验证分布式锁功能：

```bash
# 运行所有分布式锁测试
mvn test -Dtest=RedissonDistributedLockServiceTest

# 运行特定测试
mvn test -Dtest=RedissonDistributedLockServiceTest#testBasicDistributedLock
```

## 📊 监控指标

建议监控以下指标：

1. **锁获取成功率**
2. **锁等待时间分布**
3. **锁持有时间分布**
4. **锁争用次数**
5. **看门狗续期次数**

通过这些指标可以优化锁的配置和业务逻辑，提高系统性能。
