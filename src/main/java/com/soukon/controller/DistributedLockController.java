package com.soukon.controller;

import com.soukon.service.RedissonDistributedLockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 分布式锁演示控制器
 * 提供 HTTP 接口来演示各种分布式锁功能
 */
@Slf4j
@RestController
@RequestMapping("/api/distributed-lock")
public class DistributedLockController {

    private final RedissonDistributedLockService lockService;

    public DistributedLockController(RedissonDistributedLockService lockService) {
        this.lockService = lockService;
    }

    /**
     * 基础分布式锁演示
     * 
     * GET /api/distributed-lock/basic?lockKey=test&waitTime=5&leaseTime=10
     */
    @GetMapping("/basic")
    public Map<String, Object> basicLock(
            @RequestParam(defaultValue = "demo-basic-lock") String lockKey,
            @RequestParam(defaultValue = "5") long waitTime,
            @RequestParam(defaultValue = "10") long leaseTime) {
        
        log.info("🌐 收到基础分布式锁请求，lockKey: {}, waitTime: {}, leaseTime: {}", 
                lockKey, waitTime, leaseTime);
        
        Map<String, Object> response = new HashMap<>();
        long startTime = System.currentTimeMillis();
        
        try {
            boolean success = lockService.tryBasicLock(lockKey, waitTime, leaseTime);
            long duration = System.currentTimeMillis() - startTime;
            
            response.put("success", success);
            response.put("lockKey", lockKey);
            response.put("duration", duration + "ms");
            response.put("message", success ? "成功获取并释放分布式锁" : "获取分布式锁失败");
            response.put("threadName", Thread.currentThread().getName());
            
            log.info("🌐 基础分布式锁请求处理完成，结果: {}", success);
            
        } catch (Exception e) {
            log.error("🌐 基础分布式锁请求处理异常", e);
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        
        return response;
    }

    /**
     * 获取分布式锁使用说明
     */
    @GetMapping("/help")
    public Map<String, Object> getHelp() {
        Map<String, Object> help = new HashMap<>();
        
        help.put("title", "Redisson 分布式锁演示 API");
        help.put("description", "本 API 提供多种分布式锁功能的演示接口");
        
        Map<String, String> endpoints = new HashMap<>();
        endpoints.put("GET /api/distributed-lock/basic", "基础分布式锁演示");
        endpoints.put("GET /api/distributed-lock/help", "获取帮助信息");
        
        help.put("endpoints", endpoints);
        
        Map<String, String> examples = new HashMap<>();
        examples.put("基础锁", "/api/distributed-lock/basic?lockKey=myLock&waitTime=5&leaseTime=10");
        
        help.put("examples", examples);
        
        return help;
    }
}
