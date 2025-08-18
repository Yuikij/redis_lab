package com.soukon.controller;

import com.soukon.entity.User;
import com.soukon.service.CacheAsideService;
import com.soukon.service.DelayedDoubleDeleteService;
import com.soukon.service.WriteBehindService;
import com.soukon.service.WriteThroughService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 缓存一致性演示控制器
 * 
 * 提供RESTful API来演示不同的缓存一致性策略
 */
@RestController
@RequestMapping("/api/cache-consistency")
@RequiredArgsConstructor
@Slf4j
public class CacheConsistencyController {
    
    private final CacheAsideService cacheAsideService;
    private final WriteThroughService writeThroughService;
    private final WriteBehindService writeBehindService;
    private final DelayedDoubleDeleteService delayedDoubleDeleteService;
    
    /**
     * Cache Aside 模式演示
     */
    @PostMapping("/cache-aside/users")
    public ResponseEntity<User> createUserCacheAside(@RequestBody User user) {
        log.info("【API - Cache Aside】创建用户请求: {}", user.getUsername());
        User savedUser = cacheAsideService.createUser(user);
        return ResponseEntity.ok(savedUser);
    }
    
    @GetMapping("/cache-aside/users/{id}")
    public ResponseEntity<User> getUserCacheAside(@PathVariable Long id) {
        log.info("【API - Cache Aside】查询用户请求，ID: {}", id);
        Optional<User> user = cacheAsideService.getUserById(id);
        return user.map(ResponseEntity::ok)
                  .orElse(ResponseEntity.notFound().build());
    }
    
    @PutMapping("/cache-aside/users/{id}")
    public ResponseEntity<User> updateUserCacheAside(@PathVariable Long id, @RequestBody User user) {
        log.info("【API - Cache Aside】更新用户请求，ID: {}", id);
        user.setId(id);
        User updatedUser = cacheAsideService.updateUser(user);
        return ResponseEntity.ok(updatedUser);
    }
    
    @DeleteMapping("/cache-aside/users/{id}")
    public ResponseEntity<Void> deleteUserCacheAside(@PathVariable Long id) {
        log.info("【API - Cache Aside】删除用户请求，ID: {}", id);
        cacheAsideService.deleteUser(id);
        return ResponseEntity.ok().build();
    }
    
    /**
     * Write Through 模式演示
     */
    @PostMapping("/write-through/users")
    public ResponseEntity<User> createUserWriteThrough(@RequestBody User user) {
        log.info("【API - Write Through】创建用户请求: {}", user.getUsername());
        User savedUser = writeThroughService.createUser(user);
        return ResponseEntity.ok(savedUser);
    }
    
    @GetMapping("/write-through/users/{id}")
    public ResponseEntity<User> getUserWriteThrough(@PathVariable Long id) {
        log.info("【API - Write Through】查询用户请求，ID: {}", id);
        Optional<User> user = writeThroughService.getUserById(id);
        return user.map(ResponseEntity::ok)
                  .orElse(ResponseEntity.notFound().build());
    }
    
    @PutMapping("/write-through/users/{id}")
    public ResponseEntity<User> updateUserWriteThrough(@PathVariable Long id, @RequestBody User user) {
        log.info("【API - Write Through】更新用户请求，ID: {}", id);
        user.setId(id);
        User updatedUser = writeThroughService.updateUser(user);
        return ResponseEntity.ok(updatedUser);
    }
    
    @PutMapping("/write-through/users/{id}/age")
    public ResponseEntity<Map<String, Object>> updateUserAgeWriteThrough(@PathVariable Long id, @RequestParam Integer age) {
        log.info("【API - Write Through】批量更新用户年龄，ID: {}, 年龄: {}", id, age);
        writeThroughService.batchUpdateUserAge(id, age);
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "年龄更新成功");
        response.put("userId", id);
        response.put("newAge", age);
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/write-through/users/{id}/consistency")
    public ResponseEntity<Map<String, Object>> checkConsistencyWriteThrough(@PathVariable Long id) {
        log.info("【API - Write Through】检查数据一致性，ID: {}", id);
        boolean consistent = writeThroughService.checkConsistency(id);
        
        Map<String, Object> response = new HashMap<>();
        response.put("userId", id);
        response.put("consistent", consistent);
        response.put("message", consistent ? "数据一致" : "数据不一致");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Write Behind 模式演示
     */
    @PostMapping("/write-behind/users")
    public ResponseEntity<CompletableFuture<User>> createUserWriteBehind(@RequestBody User user) {
        log.info("【API - Write Behind】创建用户请求: {}", user.getUsername());
        CompletableFuture<User> future = writeBehindService.createUser(user);
        return ResponseEntity.ok(future);
    }
    
    @GetMapping("/write-behind/users/{id}")
    public ResponseEntity<User> getUserWriteBehind(@PathVariable Long id) {
        log.info("【API - Write Behind】查询用户请求，ID: {}", id);
        Optional<User> user = writeBehindService.getUserById(id);
        return user.map(ResponseEntity::ok)
                  .orElse(ResponseEntity.notFound().build());
    }
    
    @PutMapping("/write-behind/users/{id}")
    public ResponseEntity<CompletableFuture<User>> updateUserWriteBehind(@PathVariable Long id, @RequestBody User user) {
        log.info("【API - Write Behind】更新用户请求，ID: {}", id);
        user.setId(id);
        CompletableFuture<User> future = writeBehindService.updateUser(user);
        return ResponseEntity.ok(future);
    }
    
    @DeleteMapping("/write-behind/users/{id}")
    public ResponseEntity<CompletableFuture<Void>> deleteUserWriteBehind(@PathVariable Long id) {
        log.info("【API - Write Behind】删除用户请求，ID: {}", id);
        CompletableFuture<Void> future = writeBehindService.deleteUser(id);
        return ResponseEntity.ok(future);
    }
    
    @GetMapping("/write-behind/dirty-count")
    public ResponseEntity<Map<String, Object>> getDirtyDataCount() {
        log.info("【API - Write Behind】获取脏数据数量");
        long count = writeBehindService.getDirtyDataCount();
        
        Map<String, Object> response = new HashMap<>();
        response.put("dirtyDataCount", count);
        response.put("message", "当前脏数据数量: " + count);
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/write-behind/flush")
    public ResponseEntity<Map<String, String>> flushDirtyData() {
        log.info("【API - Write Behind】强制刷新脏数据");
        writeBehindService.flushAllDirtyData();
        
        Map<String, String> response = new HashMap<>();
        response.put("message", "脏数据刷新完成");
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/write-behind/users/{id}/dirty")
    public ResponseEntity<Map<String, Object>> checkIfDirty(@PathVariable Long id) {
        log.info("【API - Write Behind】检查用户是否为脏数据，ID: {}", id);
        boolean dirty = writeBehindService.isDirty(id);
        
        Map<String, Object> response = new HashMap<>();
        response.put("userId", id);
        response.put("isDirty", dirty);
        response.put("message", dirty ? "用户数据为脏数据" : "用户数据已同步");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 延时双删策略演示
     */
    @PostMapping("/delayed-double-delete/users")
    public ResponseEntity<User> createUserDelayedDoubleDelete(@RequestBody User user) {
        log.info("【API - 延时双删】创建用户请求: {}", user.getUsername());
        User savedUser = delayedDoubleDeleteService.createUser(user);
        return ResponseEntity.ok(savedUser);
    }
    
    @GetMapping("/delayed-double-delete/users/{id}")
    public ResponseEntity<User> getUserDelayedDoubleDelete(@PathVariable Long id) {
        log.info("【API - 延时双删】查询用户请求，ID: {}", id);
        Optional<User> user = delayedDoubleDeleteService.getUserById(id);
        return user.map(ResponseEntity::ok)
                  .orElse(ResponseEntity.notFound().build());
    }
    
    @PutMapping("/delayed-double-delete/users/{id}")
    public ResponseEntity<User> updateUserDelayedDoubleDelete(@PathVariable Long id, @RequestBody User user) {
        log.info("【API - 延时双删】更新用户请求，ID: {}", id);
        user.setId(id);
        User updatedUser = delayedDoubleDeleteService.updateUser(user);
        return ResponseEntity.ok(updatedUser);
    }
    
    @PutMapping("/delayed-double-delete/users/{id}/age")
    public ResponseEntity<Map<String, Object>> updateUserAgeDelayedDoubleDelete(@PathVariable Long id, @RequestParam Integer age) {
        log.info("【API - 延时双删】批量更新用户年龄，ID: {}, 年龄: {}", id, age);
        delayedDoubleDeleteService.updateUserAge(id, age);
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "年龄更新成功，延时双删已触发");
        response.put("userId", id);
        response.put("newAge", age);
        
        return ResponseEntity.ok(response);
    }
    
    @DeleteMapping("/delayed-double-delete/users/{id}")
    public ResponseEntity<Void> deleteUserDelayedDoubleDelete(@PathVariable Long id) {
        log.info("【API - 延时双删】删除用户请求，ID: {}", id);
        delayedDoubleDeleteService.deleteUser(id);
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/delayed-double-delete/users/{id}/cache-exists")
    public ResponseEntity<Map<String, Object>> checkCacheExists(@PathVariable Long id) {
        log.info("【API - 延时双删】检查缓存是否存在，ID: {}", id);
        boolean exists = delayedDoubleDeleteService.isCacheExists(id);
        
        Map<String, Object> response = new HashMap<>();
        response.put("userId", id);
        response.put("cacheExists", exists);
        response.put("message", exists ? "缓存存在" : "缓存不存在");
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/delayed-double-delete/users/{id}/warmup")
    public ResponseEntity<Map<String, String>> warmUpCache(@PathVariable Long id) {
        log.info("【API - 延时双删】缓存预热，ID: {}", id);
        delayedDoubleDeleteService.warmUpCache(id);
        
        Map<String, String> response = new HashMap<>();
        response.put("message", "缓存预热完成");
        
        return ResponseEntity.ok(response);
    }
    
    @DeleteMapping("/delayed-double-delete/users/{id}/cache")
    public ResponseEntity<Map<String, String>> evictCache(@PathVariable Long id) {
        log.info("【API - 延时双删】手动清除缓存，ID: {}", id);
        delayedDoubleDeleteService.evictCache(id);
        
        Map<String, String> response = new HashMap<>();
        response.put("message", "缓存清除完成");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 综合演示接口
     */
    @GetMapping("/strategies")
    public ResponseEntity<Map<String, String>> getStrategies() {
        Map<String, String> strategies = new HashMap<>();
        strategies.put("cache-aside", "旁路缓存 - 应用直接操作缓存，读取时先查缓存，更新时删除缓存");
        strategies.put("write-through", "写穿透 - 同时写入缓存和数据库，保证强一致性");
        strategies.put("write-behind", "写回 - 先写缓存，异步写数据库，性能最好但可能丢数据");
        strategies.put("delayed-double-delete", "延时双删 - 删除缓存→更新数据库→延时删除缓存，解决并发问题");
        
        return ResponseEntity.ok(strategies);
    }
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> status = new HashMap<>();
        status.put("status", "UP");
        status.put("message", "缓存一致性演示服务运行正常");
        
        return ResponseEntity.ok(status);
    }
}
