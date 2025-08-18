package com.soukon.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Redis事务演示服务类
 * 
 * Redis事务特点：
 * 1. 原子性：事务中的命令要么全部执行，要么全部不执行
 * 2. 一致性：保证数据的一致性状态
 * 3. 隔离性：事务执行期间其他客户端无法插入命令
 * 4. 持久性：根据Redis持久化配置决定
 * 
 * Redis事务命令：
 * - MULTI：开始事务
 * - EXEC：执行事务
 * - DISCARD：取消事务
 * - WATCH：监视键值（乐观锁）
 * - UNWATCH：取消监视
 */
@Service
public class RedisTransactionService {

    private static final Logger log = LoggerFactory.getLogger(RedisTransactionService.class);
    
    private final StringRedisTemplate stringRedisTemplate;

    public RedisTransactionService(@Qualifier("lettuceStringRedisTemplate") StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 基础事务演示：银行转账场景
     * 演示MULTI/EXEC基本事务操作
     * 
     * @param fromAccount 转出账户
     * @param toAccount 转入账户
     * @param amount 转账金额
     * @return 转账是否成功
     */
    public boolean basicTransactionDemo(String fromAccount, String toAccount, double amount) {
        log.info("=== 开始基础事务演示：银行转账 ===");
        log.info("转账参数：从账户[{}] -> 到账户[{}]，金额：{}", fromAccount, toAccount, amount);
        
        // 初始化账户余额（仅用于演示）
        String fromKey = "account:" + fromAccount;
        String toKey = "account:" + toAccount;
        stringRedisTemplate.opsForValue().setIfAbsent(fromKey, "1000.0");
        stringRedisTemplate.opsForValue().setIfAbsent(toKey, "500.0");
        
        log.info("初始余额 - 转出账户[{}]：{}，转入账户[{}]：{}", 
                fromAccount, stringRedisTemplate.opsForValue().get(fromKey),
                toAccount, stringRedisTemplate.opsForValue().get(toKey));

        try {
            // 使用SessionCallback执行事务
            List<Object> results = stringRedisTemplate.execute(new SessionCallback<List<Object>>() {
                @Override
                @SuppressWarnings({"unchecked", "rawtypes"})
                public List<Object> execute(RedisOperations operations) throws DataAccessException {
                    // 开始事务
                    operations.multi();
                    log.info("执行MULTI命令，开始事务");
                    
                    // 转出账户减少金额
                    operations.opsForValue().increment(fromKey, -amount);
                    log.info("事务中执行：账户[{}]减少金额{}", fromAccount, amount);
                    
                    // 转入账户增加金额
                    operations.opsForValue().increment(toKey, amount);
                    log.info("事务中执行：账户[{}]增加金额{}", toAccount, amount);
                    
                    // 记录转账日志
                    String logKey = "transfer:log:" + System.currentTimeMillis();
                    String logValue = String.format("转账：%s->%s，金额：%.2f，时间：%s", 
                            fromAccount, toAccount, amount, 
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                    operations.opsForValue().set(logKey, logValue, 7, TimeUnit.DAYS);
                    log.info("事务中执行：记录转账日志[{}]", logKey);
                    
                    // 执行事务
                    log.info("执行EXEC命令，提交事务");
                    return operations.exec();
                }
            });

            // 检查事务执行结果
            if (results != null && !results.isEmpty()) {
                log.info("事务执行成功，返回结果数量：{}", results.size());
                log.info("转账后余额 - 转出账户[{}]：{}，转入账户[{}]：{}", 
                        fromAccount, stringRedisTemplate.opsForValue().get(fromKey),
                        toAccount, stringRedisTemplate.opsForValue().get(toKey));
                return true;
            } else {
                log.error("事务执行失败，返回结果为空");
                return false;
            }
            
        } catch (Exception e) {
            log.error("事务执行异常：{}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 乐观锁事务演示：库存扣减场景
     * 演示WATCH/UNWATCH乐观锁机制
     * 
     * @param productId 商品ID
     * @param quantity 扣减数量
     * @return 扣减是否成功
     */
    public boolean optimisticLockDemo(String productId, int quantity) {
        log.info("=== 开始乐观锁事务演示：库存扣减 ===");
        log.info("扣减参数：商品ID[{}]，扣减数量：{}", productId, quantity);
        
        String stockKey = "product:stock:" + productId;
        String salesKey = "product:sales:" + productId;
        
        // 初始化库存（仅用于演示）
        stringRedisTemplate.opsForValue().setIfAbsent(stockKey, "100");
        stringRedisTemplate.opsForValue().setIfAbsent(salesKey, "0");
        
        log.info("初始状态 - 商品[{}]库存：{}，销量：{}", 
                productId, stringRedisTemplate.opsForValue().get(stockKey),
                stringRedisTemplate.opsForValue().get(salesKey));

        // 最大重试次数
        int maxRetries = 3;
        int retryCount = 0;
        
        while (retryCount < maxRetries) {
            try {
                log.info("第{}次尝试扣减库存", retryCount + 1);
                
                List<Object> results = stringRedisTemplate.execute(new SessionCallback<List<Object>>() {
                    @Override
                    public List<Object> execute(RedisOperations operations) throws DataAccessException {
                        // 监视库存键，实现乐观锁
                        operations.watch(stockKey);
                        log.info("执行WATCH命令，监视库存键[{}]", stockKey);
                        
                        // 获取当前库存
                        String currentStockStr = (String) operations.opsForValue().get(stockKey);
                        int currentStock = currentStockStr != null ? Integer.parseInt(currentStockStr) : 0;
                        log.info("当前库存数量：{}", currentStock);
                        
                        // 检查库存是否足够
                        if (currentStock < quantity) {
                            log.warn("库存不足，当前库存：{}，需要扣减：{}", currentStock, quantity);
                            operations.unwatch();
                            log.info("执行UNWATCH命令，取消监视");
                            return null;
                        }
                        
                        // 开始事务
                        operations.multi();
                        log.info("执行MULTI命令，开始事务");
                        
                        // 扣减库存
                        operations.opsForValue().decrement(stockKey, quantity);
                        log.info("事务中执行：库存扣减{}", quantity);
                        
                        // 增加销量
                        operations.opsForValue().increment(salesKey, quantity);
                        log.info("事务中执行：销量增加{}", quantity);
                        
                        // 记录扣减日志
                        String logKey = "stock:log:" + productId + ":" + System.currentTimeMillis();
                        String logValue = String.format("库存扣减：商品[%s]，数量：%d，时间：%s", 
                                productId, quantity, 
                                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                        operations.opsForValue().set(logKey, logValue, 1, TimeUnit.DAYS);
                        log.info("事务中执行：记录扣减日志[{}]", logKey);
                        
                        // 执行事务
                        log.info("执行EXEC命令，提交事务");
                        return operations.exec();
                    }
                });

                // 检查事务执行结果
                if (results == null) {
                    log.warn("事务被其他客户端中断（WATCH键被修改）或库存不足，准备重试");
                    retryCount++;
                    
                    // 短暂等待后重试
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.error("重试等待被中断：{}", e.getMessage());
                        break;
                    }
                    continue;
                }
                
                if (!results.isEmpty()) {
                    log.info("事务执行成功，返回结果数量：{}", results.size());
                    log.info("扣减后状态 - 商品[{}]库存：{}，销量：{}", 
                            productId, stringRedisTemplate.opsForValue().get(stockKey),
                            stringRedisTemplate.opsForValue().get(salesKey));
                    return true;
                } else {
                    log.error("事务执行失败，返回结果为空");
                    return false;
                }
                
            } catch (Exception e) {
                log.error("第{}次尝试异常：{}", retryCount + 1, e.getMessage(), e);
                retryCount++;
            }
        }
        
        log.error("达到最大重试次数{}，库存扣减失败", maxRetries);
        return false;
    }

    /**
     * 事务回滚演示：模拟执行过程中的错误
     * 演示DISCARD命令的使用
     * 
     * @param userId 用户ID
     * @param points 积分变化
     * @return 操作是否成功
     */
    public boolean transactionRollbackDemo(String userId, int points) {
        log.info("=== 开始事务回滚演示：积分变更 ===");
        log.info("操作参数：用户ID[{}]，积分变化：{}", userId, points);
        
        String pointsKey = "user:points:" + userId;
        String levelKey = "user:level:" + userId;
        
        // 初始化用户数据（仅用于演示）
        stringRedisTemplate.opsForValue().setIfAbsent(pointsKey, "1000");
        stringRedisTemplate.opsForValue().setIfAbsent(levelKey, "5");
        
        log.info("初始状态 - 用户[{}]积分：{}，等级：{}", 
                userId, stringRedisTemplate.opsForValue().get(pointsKey),
                stringRedisTemplate.opsForValue().get(levelKey));

        try {
            // 模拟业务逻辑检查
            String currentPointsStr = stringRedisTemplate.opsForValue().get(pointsKey);
            int currentPoints = currentPointsStr != null ? Integer.parseInt(currentPointsStr) : 0;
            
            // 模拟条件：如果积分变化后小于0，则回滚事务
            if (currentPoints + points < 0) {
                log.warn("积分变化后将小于0，准备演示事务回滚");
                
                stringRedisTemplate.execute(new SessionCallback<List<Object>>() {
                    @Override
                    public List<Object> execute(RedisOperations operations) throws DataAccessException {
                        // 开始事务
                        operations.multi();
                        log.info("执行MULTI命令，开始事务");
                        
                        // 变更积分
                        operations.opsForValue().increment(pointsKey, points);
                        log.info("事务中执行：积分变化{}", points);
                        
                        // 检测到业务逻辑错误，回滚事务
                        log.warn("检测到业务逻辑错误，执行DISCARD命令回滚事务");
                        operations.discard();
                        
                        return null; // discard后返回null
                    }
                });
                
                log.info("事务已回滚，数据未发生变化");
                log.info("回滚后状态 - 用户[{}]积分：{}，等级：{}", 
                        userId, stringRedisTemplate.opsForValue().get(pointsKey),
                        stringRedisTemplate.opsForValue().get(levelKey));
                return false;
            }
            
            // 正常执行事务
            List<Object> results = stringRedisTemplate.execute(new SessionCallback<List<Object>>() {
                @Override
                @SuppressWarnings({"unchecked", "rawtypes"})
                public List<Object> execute(RedisOperations operations) throws DataAccessException {
                    // 开始事务
                    operations.multi();
                    log.info("执行MULTI命令，开始事务");
                    
                    // 变更积分
                    operations.opsForValue().increment(pointsKey, points);
                    log.info("事务中执行：积分变化{}", points);
                    
                    // 根据积分调整等级（简单演示逻辑）
                    int newPoints = currentPoints + points;
                    int newLevel = newPoints / 1000 + 1; // 每1000积分一个等级
                    operations.opsForValue().set(levelKey, String.valueOf(newLevel));
                    log.info("事务中执行：等级调整为{}", newLevel);
                    
                    // 记录操作日志
                    String logKey = "points:log:" + userId + ":" + System.currentTimeMillis();
                    String logValue = String.format("积分变更：用户[%s]，变化：%d，时间：%s", 
                            userId, points, 
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                    operations.opsForValue().set(logKey, logValue, 7, TimeUnit.DAYS);
                    log.info("事务中执行：记录操作日志[{}]", logKey);
                    
                    // 执行事务
                    log.info("执行EXEC命令，提交事务");
                    return operations.exec();
                }
            });

            if (results != null && !results.isEmpty()) {
                log.info("事务执行成功，返回结果数量：{}", results.size());
                log.info("操作后状态 - 用户[{}]积分：{}，等级：{}", 
                        userId, stringRedisTemplate.opsForValue().get(pointsKey),
                        stringRedisTemplate.opsForValue().get(levelKey));
                return true;
            } else {
                log.error("事务执行失败");
                return false;
            }
            
        } catch (Exception e) {
            log.error("事务执行异常：{}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 批量操作事务演示：批量更新用户状态
     * 演示事务中的批量操作
     * 
     * @param userIds 用户ID列表
     * @param status 新状态
     * @return 更新是否成功
     */
    public boolean batchUpdateDemo(List<String> userIds, String status) {
        log.info("=== 开始批量操作事务演示：批量更新用户状态 ===");
        log.info("操作参数：用户列表{}，新状态：{}", userIds, status);
        
        if (userIds == null || userIds.isEmpty()) {
            log.warn("用户列表为空，无需执行批量更新");
            return true;
        }

        try {
            List<Object> results = stringRedisTemplate.execute(new SessionCallback<List<Object>>() {
                @Override
                @SuppressWarnings({"unchecked", "rawtypes"})
                public List<Object> execute(RedisOperations operations) throws DataAccessException {
                    // 开始事务
                    operations.multi();
                    log.info("执行MULTI命令，开始批量更新事务");
                    
                    // 批量更新用户状态
                    for (String userId : userIds) {
                        String statusKey = "user:status:" + userId;
                        operations.opsForValue().set(statusKey, status);
                        log.info("事务中执行：更新用户[{}]状态为[{}]", userId, status);
                        
                        // 更新最后活跃时间
                        String lastActiveKey = "user:last_active:" + userId;
                        String timestamp = String.valueOf(System.currentTimeMillis());
                        operations.opsForValue().set(lastActiveKey, timestamp);
                        log.info("事务中执行：更新用户[{}]最后活跃时间", userId);
                    }
                    
                    // 记录批量操作日志
                    String logKey = "batch:log:" + System.currentTimeMillis();
                    String logValue = String.format("批量状态更新：用户数量[%d]，状态[%s]，时间：%s", 
                            userIds.size(), status,
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                    operations.opsForValue().set(logKey, logValue, 7, TimeUnit.DAYS);
                    log.info("事务中执行：记录批量操作日志[{}]", logKey);
                    
                    // 执行事务
                    log.info("执行EXEC命令，提交批量更新事务");
                    return operations.exec();
                }
            });

            if (results != null && !results.isEmpty()) {
                log.info("批量更新事务执行成功，返回结果数量：{}", results.size());
                
                // 验证更新结果
                log.info("验证批量更新结果：");
                for (String userId : userIds) {
                    String statusKey = "user:status:" + userId;
                    String currentStatus = stringRedisTemplate.opsForValue().get(statusKey);
                    log.info("用户[{}]当前状态：{}", userId, currentStatus);
                }
                
                return true;
            } else {
                log.error("批量更新事务执行失败");
                return false;
            }
            
        } catch (Exception e) {
            log.error("批量更新事务执行异常：{}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 清理演示数据
     * 
     * @param prefix 键前缀
     */
    public void cleanupDemoData(String prefix) {
        log.info("清理演示数据，前缀：{}", prefix);
        try {
            stringRedisTemplate.delete(stringRedisTemplate.keys(prefix + "*"));
            log.info("演示数据清理完成");
        } catch (Exception e) {
            log.error("清理演示数据异常：{}", e.getMessage(), e);
        }
    }
}
