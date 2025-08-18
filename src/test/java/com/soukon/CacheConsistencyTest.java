package com.soukon;

import com.soukon.entity.User;
import com.soukon.repository.UserRepository;
import com.soukon.service.CacheAsideService;
import com.soukon.service.DelayedDoubleDeleteService;
import com.soukon.service.WriteBehindService;
import com.soukon.service.WriteThroughService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 缓存一致性测试类
 * 
 * 本测试类演示了四种常见的缓存与数据库一致性策略：
 * 1. Cache Aside（旁路缓存）
 * 2. Write Through（写穿透）
 * 3. Write Behind（写回）
 * 4. Delayed Double Delete（延时双删）
 * 
 * 每种策略都有其特点和适用场景，需要根据业务需求选择
 */
@SpringBootTest
@ActiveProfiles("test")
@Slf4j
class CacheConsistencyTest {
    
    @Autowired
    private CacheAsideService cacheAsideService;
    
    @Autowired
    private WriteThroughService writeThroughService;
    
    @Autowired
    private WriteBehindService writeBehindService;
    
    @Autowired
    private DelayedDoubleDeleteService delayedDoubleDeleteService;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    @BeforeEach
    void setUp() {
        // 清理测试数据
        log.info("【测试准备】清理测试数据...");
        userRepository.deleteAll();
        
        // 清理Redis数据
        try {
            var connectionFactory = redisTemplate.getConnectionFactory();
            if (connectionFactory != null) {
                var connection = connectionFactory.getConnection();
                connection.serverCommands().flushAll();
                connection.close();
            }
        } catch (Exception e) {
            log.warn("【测试准备】Redis清理失败: {}", e.getMessage());
        }
        
        log.info("【测试准备】测试数据清理完成");
    }
    
    @Test
    @DisplayName("Cache Aside 模式测试")
    void testCacheAsidePattern() {
        log.info("\n======= Cache Aside 模式测试开始 =======");
        
        // 1. 创建用户
        User user = new User("cache_aside_user", "cache@example.com", "缓存用户", 25);
        User savedUser = cacheAsideService.createUser(user);
        log.info("✅ 1. 用户创建成功，ID: {}", savedUser.getId());
        
        // 2. 第一次查询（缓存未命中，从数据库加载）
        Optional<User> firstQuery = cacheAsideService.getUserById(savedUser.getId());
        assertTrue(firstQuery.isPresent());
        assertEquals("cache_aside_user", firstQuery.get().getUsername());
        log.info("✅ 2. 第一次查询成功，触发缓存加载");
        
        // 3. 第二次查询（缓存命中）
        Optional<User> secondQuery = cacheAsideService.getUserById(savedUser.getId());
        assertTrue(secondQuery.isPresent());
        assertEquals("cache_aside_user", secondQuery.get().getUsername());
        log.info("✅ 3. 第二次查询成功，缓存命中");
        
        // 4. 更新用户（删除缓存策略）
        savedUser.setNickname("更新后的昵称");
        User updatedUser = cacheAsideService.updateUser(savedUser);
        assertEquals("更新后的昵称", updatedUser.getNickname());
        log.info("✅ 4. 用户更新成功，缓存已删除");
        
        // 5. 更新后查询（重新从数据库加载）
        Optional<User> afterUpdateQuery = cacheAsideService.getUserById(savedUser.getId());
        assertTrue(afterUpdateQuery.isPresent());
        assertEquals("更新后的昵称", afterUpdateQuery.get().getNickname());
        log.info("✅ 5. 更新后查询成功，获取到最新数据");
        
        // 6. 删除用户
        cacheAsideService.deleteUser(savedUser.getId());
        Optional<User> afterDeleteQuery = cacheAsideService.getUserById(savedUser.getId());
        assertFalse(afterDeleteQuery.isPresent());
        log.info("✅ 6. 用户删除成功");
        
        log.info("======= Cache Aside 模式测试完成 =======\n");
    }
    
    @Test
    @DisplayName("Write Through 模式测试")
    void testWriteThroughPattern() {
        log.info("\n======= Write Through 模式测试开始 =======");
        
        // 1. 创建用户（同时写入数据库和缓存）
        User user = new User("write_through_user", "writethrough@example.com", "写穿透用户", 30);
        User savedUser = writeThroughService.createUser(user);
        log.info("✅ 1. 用户创建成功，同时写入数据库和缓存，ID: {}", savedUser.getId());
        
        // 2. 查询用户（缓存命中）
        Optional<User> queryResult = writeThroughService.getUserById(savedUser.getId());
        assertTrue(queryResult.isPresent());
        assertEquals("write_through_user", queryResult.get().getUsername());
        log.info("✅ 2. 查询成功，缓存命中");
        
        // 3. 更新用户（同时更新数据库和缓存）
        savedUser.setAge(35);
        savedUser.setNickname("更新的写穿透用户");
        User updatedUser = writeThroughService.updateUser(savedUser);
        assertEquals(35, updatedUser.getAge());
        assertEquals("更新的写穿透用户", updatedUser.getNickname());
        log.info("✅ 3. 用户更新成功，数据库和缓存同时更新");
        
        // 4. 验证更新后的一致性
        Optional<User> afterUpdateQuery = writeThroughService.getUserById(savedUser.getId());
        assertTrue(afterUpdateQuery.isPresent());
        assertEquals(35, afterUpdateQuery.get().getAge());
        assertEquals("更新的写穿透用户", afterUpdateQuery.get().getNickname());
        log.info("✅ 4. 更新后查询成功，数据一致");
        
        // 5. 批量更新测试
        writeThroughService.batchUpdateUserAge(savedUser.getId(), 40);
        Optional<User> afterBatchUpdate = writeThroughService.getUserById(savedUser.getId());
        assertTrue(afterBatchUpdate.isPresent());
        assertEquals(40, afterBatchUpdate.get().getAge());
        log.info("✅ 5. 批量更新成功，缓存和数据库保持一致");
        
        // 6. 一致性检查
        boolean consistent = writeThroughService.checkConsistency(savedUser.getId());
        assertTrue(consistent);
        log.info("✅ 6. 一致性检查通过");
        
        // 7. 删除用户
        writeThroughService.deleteUser(savedUser.getId());
        Optional<User> afterDeleteQuery = writeThroughService.getUserById(savedUser.getId());
        assertFalse(afterDeleteQuery.isPresent());
        log.info("✅ 7. 用户删除成功");
        
        log.info("======= Write Through 模式测试完成 =======\n");
    }
    
    @Test
    @DisplayName("Write Behind 模式测试")
    void testWriteBehindPattern() throws Exception {
        log.info("\n======= Write Behind 模式测试开始 =======");
        
        // 1. 创建用户
        User user = new User("write_behind_user", "writebehind@example.com", "写回用户", 28);
        CompletableFuture<User> createFuture = writeBehindService.createUser(user);
        User savedUser = createFuture.get(5, TimeUnit.SECONDS);
        log.info("✅ 1. 用户创建成功，ID: {}", savedUser.getId());
        
        // 2. 查询用户
        Optional<User> queryResult = writeBehindService.getUserById(savedUser.getId());
        assertTrue(queryResult.isPresent());
        assertEquals("write_behind_user", queryResult.get().getUsername());
        log.info("✅ 2. 查询成功");
        
        // 3. 更新用户（立即更新缓存，异步更新数据库）
        savedUser.setAge(32);
        savedUser.setNickname("异步更新用户");
        CompletableFuture<User> updateFuture = writeBehindService.updateUser(savedUser);
        
        // 检查是否标记为脏数据
        assertTrue(writeBehindService.isDirty(savedUser.getId()));
        log.info("✅ 3. 用户更新请求提交，已标记为脏数据");
        
        // 等待异步更新完成
        User updatedUser = updateFuture.get(5, TimeUnit.SECONDS);
        assertEquals(32, updatedUser.getAge());
        assertEquals("异步更新用户", updatedUser.getNickname());
        log.info("✅ 4. 异步更新完成");
        
        // 等待一段时间，确保脏数据标记被清除
        Thread.sleep(200);
        assertFalse(writeBehindService.isDirty(savedUser.getId()));
        log.info("✅ 5. 脏数据标记已清除");
        
        // 4. 查询更新后的数据
        Optional<User> afterUpdateQuery = writeBehindService.getUserById(savedUser.getId());
        assertTrue(afterUpdateQuery.isPresent());
        assertEquals(32, afterUpdateQuery.get().getAge());
        log.info("✅ 6. 更新后查询成功");
        
        // 5. 测试脏数据计数
        long dirtyCount = writeBehindService.getDirtyDataCount();
        log.info("✅ 7. 当前脏数据数量: {}", dirtyCount);
        
        // 6. 删除用户
        CompletableFuture<Void> deleteFuture = writeBehindService.deleteUser(savedUser.getId());
        deleteFuture.get(5, TimeUnit.SECONDS);
        
        Optional<User> afterDeleteQuery = writeBehindService.getUserById(savedUser.getId());
        assertFalse(afterDeleteQuery.isPresent());
        log.info("✅ 8. 用户删除成功");
        
        log.info("======= Write Behind 模式测试完成 =======\n");
    }
    
    @Test
    @DisplayName("延时双删策略测试")
    void testDelayedDoubleDeleteStrategy() throws Exception {
        log.info("\n======= 延时双删策略测试开始 =======");
        
        // 1. 创建用户
        User user = new User("delayed_delete_user", "delayed@example.com", "延时双删用户", 26);
        User savedUser = delayedDoubleDeleteService.createUser(user);
        log.info("✅ 1. 用户创建成功，ID: {}", savedUser.getId());
        
        // 2. 预热缓存
        delayedDoubleDeleteService.warmUpCache(savedUser.getId());
        assertTrue(delayedDoubleDeleteService.isCacheExists(savedUser.getId()));
        log.info("✅ 2. 缓存预热成功");
        
        // 3. 更新用户（延时双删策略）
        savedUser.setAge(29);
        savedUser.setNickname("延时双删更新用户");
        User updatedUser = delayedDoubleDeleteService.updateUser(savedUser);
        assertEquals(29, updatedUser.getAge());
        log.info("✅ 3. 用户更新成功，触发延时双删");
        
        // 4. 立即查询（可能命中缓存或数据库）
        Optional<User> immediateQuery = delayedDoubleDeleteService.getUserById(savedUser.getId());
        assertTrue(immediateQuery.isPresent());
        log.info("✅ 4. 立即查询成功");
        
        // 5. 等待延时双删完成
        Thread.sleep(1500); // 等待延时删除完成
        
        // 6. 再次查询，确保获取最新数据
        Optional<User> finalQuery = delayedDoubleDeleteService.getUserById(savedUser.getId());
        assertTrue(finalQuery.isPresent());
        assertEquals(29, finalQuery.get().getAge());
        assertEquals("延时双删更新用户", finalQuery.get().getNickname());
        log.info("✅ 5. 延时双删后查询成功，数据正确");
        
        // 7. 测试批量更新
        delayedDoubleDeleteService.updateUserAge(savedUser.getId(), 31);
        Thread.sleep(1500); // 等待延时删除完成
        
        Optional<User> afterBatchUpdate = delayedDoubleDeleteService.getUserById(savedUser.getId());
        assertTrue(afterBatchUpdate.isPresent());
        assertEquals(31, afterBatchUpdate.get().getAge());
        log.info("✅ 6. 批量更新测试成功");
        
        // 8. 模拟并发读写
        log.info("✅ 7. 开始并发读写测试...");
        
        // 启动并发读取
        CompletableFuture<User> concurrentReadFuture = delayedDoubleDeleteService.simulateConcurrentRead(savedUser.getId());
        
        // 立即执行更新
        savedUser.setAge(33);
        delayedDoubleDeleteService.updateUser(savedUser);
        
        // 等待并发操作完成
        User concurrentReadResult = concurrentReadFuture.get(3, TimeUnit.SECONDS);
        log.info("✅ 8. 并发读写测试完成，读取结果: {}", concurrentReadResult != null ? "成功" : "失败");
        
        // 9. 删除用户
        delayedDoubleDeleteService.deleteUser(savedUser.getId());
        Thread.sleep(1500); // 等待延时删除完成
        
        Optional<User> afterDeleteQuery = delayedDoubleDeleteService.getUserById(savedUser.getId());
        assertFalse(afterDeleteQuery.isPresent());
        assertFalse(delayedDoubleDeleteService.isCacheExists(savedUser.getId()));
        log.info("✅ 9. 用户删除成功，缓存已清除");
        
        log.info("======= 延时双删策略测试完成 =======\n");
    }
    
    @Test
    @DisplayName("性能对比测试")
    void testPerformanceComparison() {
        log.info("\n======= 性能对比测试开始 =======");
        
        int testCount = 100;
        
        // Cache Aside 性能测试
        long cacheAsideStart = System.currentTimeMillis();
        for (int i = 0; i < testCount; i++) {
            User user = new User("perf_cache_aside_" + i, "perf" + i + "@example.com", "性能测试用户", 20 + i);
            User saved = cacheAsideService.createUser(user);
            cacheAsideService.getUserById(saved.getId());
            cacheAsideService.deleteUser(saved.getId());
        }
        long cacheAsideTime = System.currentTimeMillis() - cacheAsideStart;
        log.info("✅ Cache Aside 模式 {} 次操作耗时: {} ms", testCount, cacheAsideTime);
        
        // Write Through 性能测试
        long writeThroughStart = System.currentTimeMillis();
        for (int i = 0; i < testCount; i++) {
            User user = new User("perf_write_through_" + i, "perf" + i + "@example.com", "性能测试用户", 20 + i);
            User saved = writeThroughService.createUser(user);
            writeThroughService.getUserById(saved.getId());
            writeThroughService.deleteUser(saved.getId());
        }
        long writeThroughTime = System.currentTimeMillis() - writeThroughStart;
        log.info("✅ Write Through 模式 {} 次操作耗时: {} ms", testCount, writeThroughTime);
        
        log.info("======= 性能对比测试完成 =======\n");
        log.info("📊 性能总结:");
        log.info("   Cache Aside:  {} ms", cacheAsideTime);
        log.info("   Write Through: {} ms", writeThroughTime);
        log.info("   性能比值: {:.2f}", (double) writeThroughTime / cacheAsideTime);
    }
    
    @Test
    @DisplayName("数据一致性压力测试")
    void testConsistencyUnderPressure() throws Exception {
        log.info("\n======= 数据一致性压力测试开始 =======");
        
        // 创建测试用户
        User user = new User("consistency_test_user", "consistency@example.com", "一致性测试用户", 25);
        User savedUser = cacheAsideService.createUser(user);
        
        // 并发读写测试
        int concurrentCount = 10;
        CompletableFuture<?>[] futures = new CompletableFuture[concurrentCount];
        
        for (int i = 0; i < concurrentCount; i++) {
            final int index = i;
            futures[i] = CompletableFuture.runAsync(() -> {
                try {
                    // 并发更新
                    savedUser.setAge(25 + index);
                    cacheAsideService.updateUser(savedUser);
                    
                    // 并发查询
                    Optional<User> result = cacheAsideService.getUserById(savedUser.getId());
                    log.info("并发操作 {} 完成，查询结果存在: {}", index, result.isPresent());
                } catch (Exception e) {
                    log.error("并发操作 {} 失败: {}", index, e.getMessage());
                }
            });
        }
        
        // 等待所有并发操作完成
        CompletableFuture.allOf(futures).get(10, TimeUnit.SECONDS);
        
        // 最终一致性检查
        Optional<User> finalResult = cacheAsideService.getUserById(savedUser.getId());
        assertTrue(finalResult.isPresent());
        log.info("✅ 并发压力测试完成，最终数据存在: {}", finalResult.isPresent());
        
        // 清理
        cacheAsideService.deleteUser(savedUser.getId());
        
        log.info("======= 数据一致性压力测试完成 =======\n");
    }
}
