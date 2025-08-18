package com.soukon.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soukon.entity.User;
import com.soukon.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Write Behind 模式实现 - 写回/回写
 * 
 * 在这种模式下，写操作先写缓存，异步写数据库：
 * 1. 读取：先查缓存，缓存不存在时查数据库并写入缓存
 * 2. 写入：立即写缓存，异步写数据库
 * 3. 定期将脏数据批量同步到数据库
 * 
 * 优点：写入性能最好，减少数据库压力
 * 缺点：可能有数据丢失风险，一致性相对较弱
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WriteBehindService {
    
    private final UserRepository userRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    
    private static final String CACHE_PREFIX = "write_behind:user:";
    private static final String DIRTY_SET_KEY = "write_behind:dirty_users";
    private static final Duration CACHE_TTL = Duration.ofHours(1); // 写回模式缓存时间较长
    
    /**
     * 查询用户 - Write Behind 读取策略
     * 1. 先查缓存
     * 2. 缓存不存在时查数据库并写入缓存
     */
    public Optional<User> getUserById(Long id) {
        String cacheKey = CACHE_PREFIX + id;
        
        try {
            // 1. 先查询缓存
            log.info("【Write Behind 读取】开始查询用户ID: {}", id);
            String cachedUserJson = redisTemplate.opsForValue().get(cacheKey);
            
            if (cachedUserJson != null) {
                // 缓存命中
                User cachedUser = objectMapper.readValue(cachedUserJson, User.class);
                log.info("【Write Behind 读取】缓存命中，用户: {}", cachedUser.getUsername());
                return Optional.of(cachedUser);
            }
            
            // 2. 缓存未命中，查询数据库
            log.info("【Write Behind 读取】缓存未命中，查询数据库...");
            Optional<User> userFromDb = userRepository.findById(id);
            
            if (userFromDb.isPresent()) {
                // 3. 将数据库结果写入缓存
                String userJson = objectMapper.writeValueAsString(userFromDb.get());
                redisTemplate.opsForValue().set(cacheKey, userJson, CACHE_TTL);
                log.info("【Write Behind 读取】数据库查询成功，已写入缓存，用户: {}", userFromDb.get().getUsername());
            } else {
                log.info("【Write Behind 读取】数据库中不存在用户ID: {}", id);
            }
            
            return userFromDb;
            
        } catch (JsonProcessingException e) {
            log.error("【Write Behind 读取】JSON序列化/反序列化失败: {}", e.getMessage());
            return userRepository.findById(id);
        } catch (Exception e) {
            log.error("【Write Behind 读取】缓存操作失败，降级到数据库查询: {}", e.getMessage());
            return userRepository.findById(id);
        }
    }
    
    /**
     * 创建用户 - Write Behind 写入策略
     * 1. 立即写缓存
     * 2. 标记为脏数据
     * 3. 异步写数据库
     */
    public CompletableFuture<User> createUser(User user) {
        log.info("【Write Behind 创建】开始创建用户: {}", user.getUsername());
        
        try {
            // 先同步保存到数据库获取ID
            User savedUser = userRepository.save(user);
            log.info("【Write Behind 创建】数据库写入成功，ID: {}, 用户名: {}", savedUser.getId(), savedUser.getUsername());
            
            // 1. 立即写入缓存
            String cacheKey = CACHE_PREFIX + savedUser.getId();
            String userJson = objectMapper.writeValueAsString(savedUser);
            redisTemplate.opsForValue().set(cacheKey, userJson, CACHE_TTL);
            log.info("【Write Behind 创建】缓存写入成功，key: {}", cacheKey);
            
            return CompletableFuture.completedFuture(savedUser);
            
        } catch (Exception e) {
            log.error("【Write Behind 创建】创建用户失败: {}", e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * 更新用户 - Write Behind 更新策略
     * 1. 立即更新缓存
     * 2. 标记为脏数据
     * 3. 异步更新数据库
     */
    public CompletableFuture<User> updateUser(User user) {
        String cacheKey = CACHE_PREFIX + user.getId();
        log.info("【Write Behind 更新】开始更新用户ID: {}, 用户名: {}", user.getId(), user.getUsername());
        
        try {
            // 1. 立即更新缓存
            String userJson = objectMapper.writeValueAsString(user);
            redisTemplate.opsForValue().set(cacheKey, userJson, CACHE_TTL);
            log.info("【Write Behind 更新】缓存更新成功，key: {}", cacheKey);
            
            // 2. 标记为脏数据，等待异步写回
            redisTemplate.opsForSet().add(DIRTY_SET_KEY, user.getId().toString());
            log.info("【Write Behind 更新】标记为脏数据，用户ID: {}", user.getId());
            
            // 3. 异步更新数据库
            return asyncUpdateDatabase(user);
            
        } catch (JsonProcessingException e) {
            log.error("【Write Behind 更新】JSON序列化失败: {}", e.getMessage());
            return CompletableFuture.failedFuture(new RuntimeException("缓存更新失败", e));
        } catch (Exception e) {
            log.error("【Write Behind 更新】更新失败: {}", e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * 异步更新数据库
     */
    @Async
    CompletableFuture<User> asyncUpdateDatabase(User user) {
        try {
            // 模拟一些延迟
            Thread.sleep(100);
            
            User updatedUser = userRepository.save(user);
            log.info("【Write Behind 异步更新】数据库更新成功，用户: {}", updatedUser.getUsername());
            
            // 移除脏数据标记
            redisTemplate.opsForSet().remove(DIRTY_SET_KEY, user.getId().toString());
            log.info("【Write Behind 异步更新】移除脏数据标记，用户ID: {}", user.getId());
            
            return CompletableFuture.completedFuture(updatedUser);
            
        } catch (Exception e) {
            log.error("【Write Behind 异步更新】数据库更新失败: {}", e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * 删除用户 - Write Behind 删除策略
     * 1. 立即删除缓存
     * 2. 异步删除数据库
     */
    public CompletableFuture<Void> deleteUser(Long id) {
        String cacheKey = CACHE_PREFIX + id;
        log.info("【Write Behind 删除】开始删除用户ID: {}", id);
        
        try {
            // 1. 立即删除缓存
            Boolean deleted = redisTemplate.delete(cacheKey);
            if (Boolean.TRUE.equals(deleted)) {
                log.info("【Write Behind 删除】缓存删除成功，key: {}", cacheKey);
            } else {
                log.warn("【Write Behind 删除】缓存删除失败或缓存不存在，key: {}", cacheKey);
            }
            
            // 2. 移除脏数据标记
            redisTemplate.opsForSet().remove(DIRTY_SET_KEY, id.toString());
            
            // 3. 异步删除数据库
            return asyncDeleteFromDatabase(id);
            
        } catch (Exception e) {
            log.error("【Write Behind 删除】删除失败: {}", e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * 异步删除数据库
     */
    @Async
    CompletableFuture<Void> asyncDeleteFromDatabase(Long id) {
        try {
            // 模拟一些延迟
            Thread.sleep(100);
            
            userRepository.deleteById(id);
            log.info("【Write Behind 异步删除】数据库删除成功，用户ID: {}", id);
            
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            log.error("【Write Behind 异步删除】数据库删除失败: {}", e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * 定时任务：批量同步脏数据到数据库
     * 每30秒执行一次
     */
    @Scheduled(fixedDelay = 30000) // 30秒
    @Transactional
    public void syncDirtyDataToDatabase() {
        try {
            Set<String> dirtyUserIds = redisTemplate.opsForSet().members(DIRTY_SET_KEY);
            
            if (dirtyUserIds == null || dirtyUserIds.isEmpty()) {
                log.debug("【Write Behind 定时同步】没有脏数据需要同步");
                return;
            }
            
            log.info("【Write Behind 定时同步】开始同步脏数据，数量: {}", dirtyUserIds.size());
            
            for (String userIdStr : dirtyUserIds) {
                try {
                    Long userId = Long.parseLong(userIdStr);
                    syncSingleUserToDatabase(userId);
                } catch (NumberFormatException e) {
                    log.error("【Write Behind 定时同步】无效的用户ID: {}", userIdStr);
                    // 清除无效的脏数据标记
                    redisTemplate.opsForSet().remove(DIRTY_SET_KEY, userIdStr);
                }
            }
            
            log.info("【Write Behind 定时同步】同步完成");
            
        } catch (Exception e) {
            log.error("【Write Behind 定时同步】同步失败: {}", e.getMessage());
        }
    }
    
    /**
     * 同步单个用户数据到数据库
     */
    private void syncSingleUserToDatabase(Long userId) {
        try {
            String cacheKey = CACHE_PREFIX + userId;
            String cachedUserJson = redisTemplate.opsForValue().get(cacheKey);
            
            if (cachedUserJson == null) {
                log.warn("【Write Behind 单个同步】缓存中不存在用户ID: {}", userId);
                // 清除脏数据标记
                redisTemplate.opsForSet().remove(DIRTY_SET_KEY, userId.toString());
                return;
            }
            
            User cachedUser = objectMapper.readValue(cachedUserJson, User.class);
            User savedUser = userRepository.save(cachedUser);
            log.info("【Write Behind 单个同步】同步成功，用户: {}", savedUser.getUsername());
            
            // 移除脏数据标记
            redisTemplate.opsForSet().remove(DIRTY_SET_KEY, userId.toString());
            
        } catch (JsonProcessingException e) {
            log.error("【Write Behind 单个同步】JSON反序列化失败，用户ID: {}, 错误: {}", userId, e.getMessage());
        } catch (Exception e) {
            log.error("【Write Behind 单个同步】同步失败，用户ID: {}, 错误: {}", userId, e.getMessage());
        }
    }
    
    /**
     * 强制刷新所有脏数据到数据库
     */
    public void flushAllDirtyData() {
        log.info("【Write Behind 强制刷新】开始强制刷新所有脏数据");
        syncDirtyDataToDatabase();
    }
    
    /**
     * 获取当前脏数据数量
     */
    public long getDirtyDataCount() {
        Long count = redisTemplate.opsForSet().size(DIRTY_SET_KEY);
        return count != null ? count : 0;
    }
    
    /**
     * 检查指定用户是否为脏数据
     */
    public boolean isDirty(Long userId) {
        Boolean exists = redisTemplate.opsForSet().isMember(DIRTY_SET_KEY, userId.toString());
        return Boolean.TRUE.equals(exists);
    }
}
