package com.soukon.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soukon.entity.User;
import com.soukon.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Optional;

/**
 * Cache Aside 模式实现 - 旁路缓存
 * 
 * 这是最常用的缓存策略，应用程序直接与缓存交互：
 * 1. 读取：先查缓存，缓存不存在时查数据库，然后将结果写入缓存
 * 2. 更新：先更新数据库，然后删除缓存（让下次读取时重新加载）
 * 
 * 优点：实现简单，容错性好
 * 缺点：可能存在短暂的数据不一致
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CacheAsideService {
    
    private final UserRepository userRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    
    private static final String CACHE_PREFIX = "cache_aside:user:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);
    
    /**
     * 查询用户 - Cache Aside 读取策略
     * 1. 先查缓存
     * 2. 缓存不存在时查数据库
     * 3. 将数据库结果写入缓存
     */
    public Optional<User> getUserById(Long id) {
        String cacheKey = CACHE_PREFIX + id;
        
        try {
            // 1. 先查询缓存
            log.info("【Cache Aside 读取】开始查询用户ID: {}", id);
            String cachedUserJson = redisTemplate.opsForValue().get(cacheKey);
            
            if (cachedUserJson != null) {
                // 缓存命中
                User cachedUser = objectMapper.readValue(cachedUserJson, User.class);
                log.info("【Cache Aside 读取】缓存命中，用户: {}", cachedUser.getUsername());
                return Optional.of(cachedUser);
            }
            
            // 2. 缓存未命中，查询数据库
            log.info("【Cache Aside 读取】缓存未命中，查询数据库...");
            Optional<User> userFromDb = userRepository.findById(id);
            
            if (userFromDb.isPresent()) {
                // 3. 将数据库结果写入缓存
                String userJson = objectMapper.writeValueAsString(userFromDb.get());
                redisTemplate.opsForValue().set(cacheKey, userJson, CACHE_TTL);
                log.info("【Cache Aside 读取】数据库查询成功，已写入缓存，用户: {}", userFromDb.get().getUsername());
            } else {
                log.info("【Cache Aside 读取】数据库中不存在用户ID: {}", id);
            }
            
            return userFromDb;
            
        } catch (JsonProcessingException e) {
            log.error("【Cache Aside 读取】JSON序列化/反序列化失败: {}", e.getMessage());
            // JSON处理失败时，直接查询数据库
            return userRepository.findById(id);
        } catch (Exception e) {
            log.error("【Cache Aside 读取】缓存操作失败，降级到数据库查询: {}", e.getMessage());
            // 缓存操作失败时，降级到数据库查询
            return userRepository.findById(id);
        }
    }
    
    /**
     * 创建用户 - Cache Aside 写入策略
     * 1. 先写数据库
     * 2. 不主动写缓存（让下次读取时加载）
     */
    @Transactional
    public User createUser(User user) {
        log.info("【Cache Aside 创建】开始创建用户: {}", user.getUsername());
        
        try {
            // 1. 先写数据库
            User savedUser = userRepository.save(user);
            log.info("【Cache Aside 创建】用户创建成功，ID: {}, 用户名: {}", savedUser.getId(), savedUser.getUsername());
            
            // 注意：创建时不主动写缓存，让下次读取时加载最新数据
            
            return savedUser;
            
        } catch (Exception e) {
            log.error("【Cache Aside 创建】用户创建失败: {}", e.getMessage());
            throw e;
        }
    }
    
    /**
     * 更新用户 - Cache Aside 更新策略
     * 1. 先更新数据库
     * 2. 删除缓存（让下次读取时重新加载）
     */
    @Transactional
    public User updateUser(User user) {
        String cacheKey = CACHE_PREFIX + user.getId();
        log.info("【Cache Aside 更新】开始更新用户ID: {}, 用户名: {}", user.getId(), user.getUsername());
        
        try {
            // 1. 先更新数据库
            User updatedUser = userRepository.save(user);
            log.info("【Cache Aside 更新】数据库更新成功，用户: {}", updatedUser.getUsername());
            
            // 2. 删除缓存，让下次读取时重新加载
            Boolean deleted = redisTemplate.delete(cacheKey);
            if (Boolean.TRUE.equals(deleted)) {
                log.info("【Cache Aside 更新】缓存删除成功，key: {}", cacheKey);
            } else {
                log.warn("【Cache Aside 更新】缓存删除失败或缓存不存在，key: {}", cacheKey);
            }
            
            return updatedUser;
            
        } catch (Exception e) {
            log.error("【Cache Aside 更新】更新失败: {}", e.getMessage());
            throw e;
        }
    }
    
    /**
     * 删除用户 - Cache Aside 删除策略
     * 1. 先删除数据库
     * 2. 删除缓存
     */
    @Transactional
    public void deleteUser(Long id) {
        String cacheKey = CACHE_PREFIX + id;
        log.info("【Cache Aside 删除】开始删除用户ID: {}", id);
        
        try {
            // 1. 先删除数据库
            userRepository.deleteById(id);
            log.info("【Cache Aside 删除】数据库删除成功，用户ID: {}", id);
            
            // 2. 删除缓存
            Boolean deleted = redisTemplate.delete(cacheKey);
            if (Boolean.TRUE.equals(deleted)) {
                log.info("【Cache Aside 删除】缓存删除成功，key: {}", cacheKey);
            } else {
                log.warn("【Cache Aside 删除】缓存删除失败或缓存不存在，key: {}", cacheKey);
            }
            
        } catch (Exception e) {
            log.error("【Cache Aside 删除】删除失败: {}", e.getMessage());
            throw e;
        }
    }
    
    /**
     * 清空指定用户的缓存
     */
    public void evictUserCache(Long id) {
        String cacheKey = CACHE_PREFIX + id;
        Boolean deleted = redisTemplate.delete(cacheKey);
        log.info("【Cache Aside 缓存清除】用户ID: {}, 删除结果: {}", id, deleted);
    }
    
    /**
     * 预热缓存 - 主动将数据库数据加载到缓存
     */
    public void warmUpCache(Long id) {
        log.info("【Cache Aside 预热】开始预热用户ID: {} 的缓存", id);
        getUserById(id); // 触发缓存加载
    }
}
