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
 * Write Through 模式实现 - 写穿透
 * 
 * 在这种模式下，缓存作为主要的数据访问层：
 * 1. 读取：先查缓存，缓存不存在时查数据库并写入缓存
 * 2. 写入：同时写入缓存和数据库，保证强一致性
 * 
 * 优点：数据一致性强，读取性能好
 * 缺点：写入性能较差（需要同时写两个地方），缓存故障影响写入
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WriteThroughService {
    
    private final UserRepository userRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    
    private static final String CACHE_PREFIX = "write_through:user:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);
    
    /**
     * 查询用户 - Write Through 读取策略
     * 1. 先查缓存
     * 2. 缓存不存在时查数据库并写入缓存
     */
    public Optional<User> getUserById(Long id) {
        String cacheKey = CACHE_PREFIX + id;
        
        try {
            // 1. 先查询缓存
            log.info("【Write Through 读取】开始查询用户ID: {}", id);
            String cachedUserJson = redisTemplate.opsForValue().get(cacheKey);
            
            if (cachedUserJson != null) {
                // 缓存命中
                User cachedUser = objectMapper.readValue(cachedUserJson, User.class);
                log.info("【Write Through 读取】缓存命中，用户: {}", cachedUser.getUsername());
                return Optional.of(cachedUser);
            }
            
            // 2. 缓存未命中，查询数据库
            log.info("【Write Through 读取】缓存未命中，查询数据库...");
            Optional<User> userFromDb = userRepository.findById(id);
            
            if (userFromDb.isPresent()) {
                // 3. 将数据库结果写入缓存
                String userJson = objectMapper.writeValueAsString(userFromDb.get());
                redisTemplate.opsForValue().set(cacheKey, userJson, CACHE_TTL);
                log.info("【Write Through 读取】数据库查询成功，已写入缓存，用户: {}", userFromDb.get().getUsername());
            } else {
                log.info("【Write Through 读取】数据库中不存在用户ID: {}", id);
            }
            
            return userFromDb;
            
        } catch (JsonProcessingException e) {
            log.error("【Write Through 读取】JSON序列化/反序列化失败: {}", e.getMessage());
            return userRepository.findById(id);
        } catch (Exception e) {
            log.error("【Write Through 读取】缓存操作失败，降级到数据库查询: {}", e.getMessage());
            return userRepository.findById(id);
        }
    }
    
    /**
     * 创建用户 - Write Through 写入策略
     * 1. 同时写入数据库和缓存
     * 2. 保证强一致性
     */
    @Transactional
    public User createUser(User user) {
        log.info("【Write Through 创建】开始创建用户: {}", user.getUsername());
        
        try {
            // 1. 先写数据库
            User savedUser = userRepository.save(user);
            log.info("【Write Through 创建】数据库写入成功，ID: {}, 用户名: {}", savedUser.getId(), savedUser.getUsername());
            
            // 2. 立即写入缓存
            String cacheKey = CACHE_PREFIX + savedUser.getId();
            String userJson = objectMapper.writeValueAsString(savedUser);
            redisTemplate.opsForValue().set(cacheKey, userJson, CACHE_TTL);
            log.info("【Write Through 创建】缓存写入成功，key: {}", cacheKey);
            
            return savedUser;
            
        } catch (JsonProcessingException e) {
            log.error("【Write Through 创建】JSON序列化失败: {}", e.getMessage());
            throw new RuntimeException("缓存写入失败", e);
        } catch (Exception e) {
            log.error("【Write Through 创建】创建用户失败: {}", e.getMessage());
            throw e;
        }
    }
    
    /**
     * 更新用户 - Write Through 更新策略
     * 1. 同时更新数据库和缓存
     * 2. 保证强一致性
     */
    @Transactional
    public User updateUser(User user) {
        String cacheKey = CACHE_PREFIX + user.getId();
        log.info("【Write Through 更新】开始更新用户ID: {}, 用户名: {}", user.getId(), user.getUsername());
        
        try {
            // 1. 先更新数据库
            User updatedUser = userRepository.save(user);
            log.info("【Write Through 更新】数据库更新成功，用户: {}", updatedUser.getUsername());
            
            // 2. 立即更新缓存
            String userJson = objectMapper.writeValueAsString(updatedUser);
            redisTemplate.opsForValue().set(cacheKey, userJson, CACHE_TTL);
            log.info("【Write Through 更新】缓存更新成功，key: {}", cacheKey);
            
            return updatedUser;
            
        } catch (JsonProcessingException e) {
            log.error("【Write Through 更新】JSON序列化失败: {}", e.getMessage());
            throw new RuntimeException("缓存更新失败", e);
        } catch (Exception e) {
            log.error("【Write Through 更新】更新失败: {}", e.getMessage());
            throw e;
        }
    }
    
    /**
     * 删除用户 - Write Through 删除策略
     * 1. 同时删除数据库和缓存
     */
    @Transactional
    public void deleteUser(Long id) {
        String cacheKey = CACHE_PREFIX + id;
        log.info("【Write Through 删除】开始删除用户ID: {}", id);
        
        try {
            // 1. 先删除数据库
            userRepository.deleteById(id);
            log.info("【Write Through 删除】数据库删除成功，用户ID: {}", id);
            
            // 2. 立即删除缓存
            Boolean deleted = redisTemplate.delete(cacheKey);
            if (Boolean.TRUE.equals(deleted)) {
                log.info("【Write Through 删除】缓存删除成功，key: {}", cacheKey);
            } else {
                log.warn("【Write Through 删除】缓存删除失败或缓存不存在，key: {}", cacheKey);
            }
            
        } catch (Exception e) {
            log.error("【Write Through 删除】删除失败: {}", e.getMessage());
            throw e;
        }
    }
    
    /**
     * 批量更新用户年龄 - 演示Write Through在批量操作中的表现
     */
    @Transactional
    public void batchUpdateUserAge(Long id, Integer newAge) {
        log.info("【Write Through 批量更新】开始更新用户ID: {} 的年龄为: {}", id, newAge);
        
        try {
            // 1. 更新数据库
            int affectedRows = userRepository.updateAgeById(id, newAge);
            if (affectedRows > 0) {
                log.info("【Write Through 批量更新】数据库更新成功，影响行数: {}", affectedRows);
                
                // 2. 更新缓存 - 需要先查询完整用户信息
                Optional<User> userOpt = userRepository.findById(id);
                if (userOpt.isPresent()) {
                    String cacheKey = CACHE_PREFIX + id;
                    String userJson = objectMapper.writeValueAsString(userOpt.get());
                    redisTemplate.opsForValue().set(cacheKey, userJson, CACHE_TTL);
                    log.info("【Write Through 批量更新】缓存更新成功，用户: {}", userOpt.get().getUsername());
                } else {
                    log.warn("【Write Through 批量更新】用户不存在，无法更新缓存");
                }
            } else {
                log.warn("【Write Through 批量更新】数据库更新失败，没有记录被更新");
            }
            
        } catch (JsonProcessingException e) {
            log.error("【Write Through 批量更新】JSON序列化失败: {}", e.getMessage());
            throw new RuntimeException("缓存更新失败", e);
        } catch (Exception e) {
            log.error("【Write Through 批量更新】批量更新失败: {}", e.getMessage());
            throw e;
        }
    }
    
    /**
     * 检查缓存和数据库的一致性
     */
    public boolean checkConsistency(Long id) {
        try {
            String cacheKey = CACHE_PREFIX + id;
            String cachedUserJson = redisTemplate.opsForValue().get(cacheKey);
            Optional<User> userFromDb = userRepository.findById(id);
            
            if (cachedUserJson == null && userFromDb.isEmpty()) {
                log.info("【Write Through 一致性检查】缓存和数据库都为空，一致");
                return true;
            }
            
            if (cachedUserJson == null || userFromDb.isEmpty()) {
                log.warn("【Write Through 一致性检查】缓存和数据库状态不一致");
                return false;
            }
            
            User cachedUser = objectMapper.readValue(cachedUserJson, User.class);
            User dbUser = userFromDb.get();
            
            boolean consistent = cachedUser.equals(dbUser);
            log.info("【Write Through 一致性检查】用户ID: {}, 一致性: {}", id, consistent);
            
            return consistent;
            
        } catch (Exception e) {
            log.error("【Write Through 一致性检查】检查失败: {}", e.getMessage());
            return false;
        }
    }
}
