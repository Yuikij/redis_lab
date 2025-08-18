package com.soukon.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soukon.entity.User;
import com.soukon.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 延时双删策略实现
 * 
 * 这是一种专门解决缓存与数据库一致性问题的策略：
 * 1. 读取：先查缓存，缓存不存在时查数据库并写入缓存
 * 2. 更新：删除缓存 -> 更新数据库 -> 延时删除缓存
 * 3. 删除：删除缓存 -> 删除数据库 -> 延时删除缓存
 * 
 * 延时删除的目的：
 * - 防止在数据库更新期间，其他线程从数据库读取旧数据并写入缓存
 * - 确保数据库更新完成后，清除可能的脏缓存
 * 
 * 优点：有效解决读写并发导致的数据不一致
 * 缺点：延时期间可能读到旧数据，实现复杂度较高
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DelayedDoubleDeleteService {
    
    private final UserRepository userRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    
    private static final String CACHE_PREFIX = "delayed_double_delete:user:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);
    private static final long DELAY_MILLISECONDS = 1000; // 延时1秒删除
    
    /**
     * 查询用户 - 标准的缓存读取策略
     */
    public Optional<User> getUserById(Long id) {
        String cacheKey = CACHE_PREFIX + id;
        
        try {
            // 1. 先查询缓存
            log.info("【延时双删 读取】开始查询用户ID: {}", id);
            String cachedUserJson = redisTemplate.opsForValue().get(cacheKey);
            
            if (cachedUserJson != null) {
                // 缓存命中
                User cachedUser = objectMapper.readValue(cachedUserJson, User.class);
                log.info("【延时双删 读取】缓存命中，用户: {}", cachedUser.getUsername());
                return Optional.of(cachedUser);
            }
            
            // 2. 缓存未命中，查询数据库
            log.info("【延时双删 读取】缓存未命中，查询数据库...");
            Optional<User> userFromDb = userRepository.findById(id);
            
            if (userFromDb.isPresent()) {
                // 3. 将数据库结果写入缓存
                String userJson = objectMapper.writeValueAsString(userFromDb.get());
                redisTemplate.opsForValue().set(cacheKey, userJson, CACHE_TTL);
                log.info("【延时双删 读取】数据库查询成功，已写入缓存，用户: {}", userFromDb.get().getUsername());
            } else {
                log.info("【延时双删 读取】数据库中不存在用户ID: {}", id);
            }
            
            return userFromDb;
            
        } catch (JsonProcessingException e) {
            log.error("【延时双删 读取】JSON序列化/反序列化失败: {}", e.getMessage());
            return userRepository.findById(id);
        } catch (Exception e) {
            log.error("【延时双删 读取】缓存操作失败，降级到数据库查询: {}", e.getMessage());
            return userRepository.findById(id);
        }
    }
    
    /**
     * 创建用户 - 简单的先数据库后缓存策略
     */
    @Transactional
    public User createUser(User user) {
        log.info("【延时双删 创建】开始创建用户: {}", user.getUsername());
        
        try {
            // 创建操作：先写数据库
            User savedUser = userRepository.save(user);
            log.info("【延时双删 创建】数据库写入成功，ID: {}, 用户名: {}", savedUser.getId(), savedUser.getUsername());
            
            // 创建操作不需要删除缓存，让后续读取时加载
            
            return savedUser;
            
        } catch (Exception e) {
            log.error("【延时双删 创建】创建用户失败: {}", e.getMessage());
            throw e;
        }
    }
    
    /**
     * 更新用户 - 延时双删策略
     * 1. 第一次删除缓存
     * 2. 更新数据库
     * 3. 延时后第二次删除缓存
     */
    @Transactional
    public User updateUser(User user) {
        String cacheKey = CACHE_PREFIX + user.getId();
        log.info("【延时双删 更新】开始更新用户ID: {}, 用户名: {}", user.getId(), user.getUsername());
        
        try {
            // 第一步：删除缓存
            Boolean firstDelete = redisTemplate.delete(cacheKey);
            log.info("【延时双删 更新】第一次删除缓存，key: {}, 结果: {}", cacheKey, firstDelete);
            
            // 第二步：更新数据库
            User updatedUser = userRepository.save(user);
            log.info("【延时双删 更新】数据库更新成功，用户: {}", updatedUser.getUsername());
            
            // 第三步：异步延时删除缓存
            asyncDelayedCacheDelete(cacheKey, user.getId());
            
            return updatedUser;
            
        } catch (Exception e) {
            log.error("【延时双删 更新】更新失败: {}", e.getMessage());
            throw e;
        }
    }
    
    /**
     * 批量更新用户年龄 - 延时双删策略
     * 演示批量操作中的缓存一致性处理
     */
    @Transactional
    public void updateUserAge(Long id, Integer newAge) {
        String cacheKey = CACHE_PREFIX + id;
        log.info("【延时双删 批量更新】开始更新用户ID: {} 的年龄为: {}", id, newAge);
        
        try {
            // 第一步：删除缓存
            Boolean firstDelete = redisTemplate.delete(cacheKey);
            log.info("【延时双删 批量更新】第一次删除缓存，key: {}, 结果: {}", cacheKey, firstDelete);
            
            // 第二步：更新数据库
            int affectedRows = userRepository.updateAgeById(id, newAge);
            if (affectedRows > 0) {
                log.info("【延时双删 批量更新】数据库更新成功，影响行数: {}", affectedRows);
                
                // 第三步：异步延时删除缓存
                asyncDelayedCacheDelete(cacheKey, id);
            } else {
                log.warn("【延时双删 批量更新】数据库更新失败，没有记录被更新");
            }
            
        } catch (Exception e) {
            log.error("【延时双删 批量更新】批量更新失败: {}", e.getMessage());
            throw e;
        }
    }
    
    /**
     * 删除用户 - 延时双删策略
     */
    @Transactional
    public void deleteUser(Long id) {
        String cacheKey = CACHE_PREFIX + id;
        log.info("【延时双删 删除】开始删除用户ID: {}", id);
        
        try {
            // 第一步：删除缓存
            Boolean firstDelete = redisTemplate.delete(cacheKey);
            log.info("【延时双删 删除】第一次删除缓存，key: {}, 结果: {}", cacheKey, firstDelete);
            
            // 第二步：删除数据库
            userRepository.deleteById(id);
            log.info("【延时双删 删除】数据库删除成功，用户ID: {}", id);
            
            // 第三步：异步延时删除缓存
            asyncDelayedCacheDelete(cacheKey, id);
            
        } catch (Exception e) {
            log.error("【延时双删 删除】删除失败: {}", e.getMessage());
            throw e;
        }
    }
    
    /**
     * 异步延时删除缓存
     * 这是延时双删策略的核心：在数据库操作完成后，延时删除可能存在的脏缓存
     */
    @Async
    public CompletableFuture<Void> asyncDelayedCacheDelete(String cacheKey, Long userId) {
        try {
            log.info("【延时双删 异步删除】开始延时 {} 毫秒后删除缓存，用户ID: {}", DELAY_MILLISECONDS, userId);
            
            // 延时等待
            Thread.sleep(DELAY_MILLISECONDS);
            
            // 第二次删除缓存
            Boolean secondDelete = redisTemplate.delete(cacheKey);
            log.info("【延时双删 异步删除】第二次删除缓存完成，key: {}, 结果: {}", cacheKey, secondDelete);
            
            return CompletableFuture.completedFuture(null);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("【延时双删 异步删除】延时被中断: {}", e.getMessage());
            return CompletableFuture.failedFuture(e);
        } catch (Exception e) {
            log.error("【延时双删 异步删除】延时删除失败: {}", e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * 模拟并发读写场景
     * 用于测试延时双删在并发情况下的效果
     */
    @Async
    public CompletableFuture<User> simulateConcurrentRead(Long id) {
        try {
            // 模拟在更新过程中的并发读取
            Thread.sleep(500); // 在延时删除之前读取
            
            Optional<User> user = getUserById(id);
            log.info("【延时双删 并发读取】并发读取结果，用户存在: {}", user.isPresent());
            
            return CompletableFuture.completedFuture(user.orElse(null));
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("【延时双删 并发读取】并发读取被中断: {}", e.getMessage());
            return CompletableFuture.failedFuture(e);
        } catch (Exception e) {
            log.error("【延时双删 并发读取】并发读取失败: {}", e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * 检查缓存状态
     */
    public boolean isCacheExists(Long id) {
        String cacheKey = CACHE_PREFIX + id;
        return Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey));
    }
    
    /**
     * 手动清除缓存
     */
    public void evictCache(Long id) {
        String cacheKey = CACHE_PREFIX + id;
        Boolean deleted = redisTemplate.delete(cacheKey);
        log.info("【延时双删 手动清除】清除缓存，key: {}, 结果: {}", cacheKey, deleted);
    }
    
    /**
     * 预热缓存
     */
    public void warmUpCache(Long id) {
        log.info("【延时双删 预热】开始预热用户ID: {} 的缓存", id);
        getUserById(id); // 触发缓存加载
    }
}
