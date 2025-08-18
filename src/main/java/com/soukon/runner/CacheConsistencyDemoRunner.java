package com.soukon.runner;

import com.soukon.entity.User;
import com.soukon.service.CacheAsideService;
import com.soukon.service.DelayedDoubleDeleteService;
import com.soukon.service.WriteBehindService;
import com.soukon.service.WriteThroughService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 缓存一致性演示运行器
 * 
 * 启动应用时自动运行演示程序，展示各种缓存一致性策略的使用
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CacheConsistencyDemoRunner implements CommandLineRunner {
    
    private final CacheAsideService cacheAsideService;
    private final WriteThroughService writeThroughService;
    private final WriteBehindService writeBehindService;
    private final DelayedDoubleDeleteService delayedDoubleDeleteService;
    
    @Override
    public void run(String... args) throws Exception {
        if (args.length > 0 && "demo".equals(args[0])) {
            log.info("\n" +
                    "==============================================\n" +
                    "    Redis 缓存与数据库一致性策略演示\n" +
                    "==============================================");
            
            runCacheAsideDemo();
            runWriteThroughDemo();
            runWriteBehindDemo();
            runDelayedDoubleDeleteDemo();
            
            log.info("\n" +
                    "==============================================\n" +
                    "             演示完成！\n" +
                    "==============================================\n" +
                    "💡 提示：\n" +
                    "1. 查看上方日志了解各种策略的执行过程\n" +
                    "2. 访问 http://localhost:8080/api/cache-consistency/strategies 查看API文档\n" +
                    "3. 访问 http://localhost:8080/h2-console 查看数据库（JDBC URL: jdbc:h2:mem:testdb）\n" +
                    "4. 运行测试：mvn test -Dtest=CacheConsistencyTest\n" +
                    "==============================================");
        }
    }
    
    /**
     * Cache Aside 模式演示
     */
    private void runCacheAsideDemo() throws InterruptedException {
        log.info("\n🚀 === Cache Aside 模式演示 ===");
        
        try {
            // 创建用户
            User user = new User("demo_cache_aside", "cache@demo.com", "演示用户", 25);
            User savedUser = cacheAsideService.createUser(user);
            
            // 第一次查询（缓存miss）
            Optional<User> firstQuery = cacheAsideService.getUserById(savedUser.getId());
            log.info("第一次查询结果: {}", firstQuery.isPresent() ? "成功" : "失败");
            
            // 第二次查询（缓存hit）
            Optional<User> secondQuery = cacheAsideService.getUserById(savedUser.getId());
            log.info("第二次查询结果: {}", secondQuery.isPresent() ? "成功（缓存命中）" : "失败");
            
            // 更新用户
            savedUser.setNickname("Cache Aside 更新");
            cacheAsideService.updateUser(savedUser);
            
            // 更新后查询
            Optional<User> afterUpdate = cacheAsideService.getUserById(savedUser.getId());
            log.info("更新后查询结果: {}", afterUpdate.isPresent() ? "成功" : "失败");
            
            // 清理
            cacheAsideService.deleteUser(savedUser.getId());
            log.info("✅ Cache Aside 演示完成");
            
        } catch (Exception e) {
            log.error("❌ Cache Aside 演示失败: {}", e.getMessage());
        }
    }
    
    /**
     * Write Through 模式演示
     */
    private void runWriteThroughDemo() throws InterruptedException {
        log.info("\n🚀 === Write Through 模式演示 ===");
        
        try {
            // 创建用户
            User user = new User("demo_write_through", "writethrough@demo.com", "写穿透演示", 30);
            User savedUser = writeThroughService.createUser(user);
            
            // 查询用户（缓存应该已存在）
            Optional<User> queryResult = writeThroughService.getUserById(savedUser.getId());
            log.info("查询结果: {}", queryResult.isPresent() ? "成功（缓存命中）" : "失败");
            
            // 更新用户
            savedUser.setAge(35);
            writeThroughService.updateUser(savedUser);
            
            // 验证更新
            Optional<User> afterUpdate = writeThroughService.getUserById(savedUser.getId());
            log.info("更新验证: 年龄 = {}", afterUpdate.map(User::getAge).orElse(-1));
            
            // 一致性检查
            boolean consistent = writeThroughService.checkConsistency(savedUser.getId());
            log.info("一致性检查: {}", consistent ? "通过" : "失败");
            
            // 清理
            writeThroughService.deleteUser(savedUser.getId());
            log.info("✅ Write Through 演示完成");
            
        } catch (Exception e) {
            log.error("❌ Write Through 演示失败: {}", e.getMessage());
        }
    }
    
    /**
     * Write Behind 模式演示
     */
    private void runWriteBehindDemo() throws InterruptedException {
        log.info("\n🚀 === Write Behind 模式演示 ===");
        
        try {
            // 创建用户
            User user = new User("demo_write_behind", "writebehind@demo.com", "写回演示", 28);
            var createFuture = writeBehindService.createUser(user);
            User savedUser = createFuture.get();
            
            // 更新用户（异步写数据库）
            savedUser.setAge(32);
            var updateFuture = writeBehindService.updateUser(savedUser);
            
            // 检查脏数据
            boolean isDirty = writeBehindService.isDirty(savedUser.getId());
            log.info("脏数据检查: {}", isDirty ? "是脏数据" : "已同步");
            
            // 等待异步操作完成
            updateFuture.get();
            Thread.sleep(200); // 等待脏数据清理
            
            boolean isDirtyAfter = writeBehindService.isDirty(savedUser.getId());
            log.info("异步完成后检查: {}", isDirtyAfter ? "仍为脏数据" : "已同步");
            
            // 脏数据统计
            long dirtyCount = writeBehindService.getDirtyDataCount();
            log.info("当前脏数据数量: {}", dirtyCount);
            
            // 清理
            var deleteFuture = writeBehindService.deleteUser(savedUser.getId());
            deleteFuture.get();
            log.info("✅ Write Behind 演示完成");
            
        } catch (Exception e) {
            log.error("❌ Write Behind 演示失败: {}", e.getMessage());
        }
    }
    
    /**
     * 延时双删策略演示
     */
    private void runDelayedDoubleDeleteDemo() throws InterruptedException {
        log.info("\n🚀 === 延时双删策略演示 ===");
        
        try {
            // 创建用户
            User user = new User("demo_delayed_delete", "delayed@demo.com", "延时双删演示", 26);
            User savedUser = delayedDoubleDeleteService.createUser(user);
            
            // 预热缓存
            delayedDoubleDeleteService.warmUpCache(savedUser.getId());
            boolean cacheExists = delayedDoubleDeleteService.isCacheExists(savedUser.getId());
            log.info("缓存预热: {}", cacheExists ? "成功" : "失败");
            
            // 更新用户（触发延时双删）
            savedUser.setAge(29);
            delayedDoubleDeleteService.updateUser(savedUser);
            
            // 立即查询
            Optional<User> immediateQuery = delayedDoubleDeleteService.getUserById(savedUser.getId());
            log.info("立即查询: {}", immediateQuery.isPresent() ? "成功" : "失败");
            
            // 等待延时双删完成
            Thread.sleep(1200);
            
            // 延时后查询
            Optional<User> delayedQuery = delayedDoubleDeleteService.getUserById(savedUser.getId());
            log.info("延时后查询: 年龄 = {}", delayedQuery.map(User::getAge).orElse(-1));
            
            // 清理
            delayedDoubleDeleteService.deleteUser(savedUser.getId());
            Thread.sleep(1200); // 等待延时删除完成
            log.info("✅ 延时双删演示完成");
            
        } catch (Exception e) {
            log.error("❌ 延时双删演示失败: {}", e.getMessage());
        }
    }
}
