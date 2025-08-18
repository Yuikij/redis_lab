package com.soukon.controller;

import com.soukon.service.RedisTransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Redis事务演示控制器
 * 提供HTTP接口演示各种Redis事务操作场景
 */
@RestController
@RequestMapping("/redis/transaction")
public class RedisTransactionController {

    private static final Logger log = LoggerFactory.getLogger(RedisTransactionController.class);
    
    private final RedisTransactionService transactionService;

    public RedisTransactionController(RedisTransactionService transactionService) {
        this.transactionService = transactionService;
    }

    /**
     * 基础事务演示：银行转账
     * 
     * @param fromAccount 转出账户
     * @param toAccount 转入账户  
     * @param amount 转账金额
     * @return 转账结果
     */
    @PostMapping("/transfer")
    public Map<String, Object> bankTransfer(
            @RequestParam String fromAccount,
            @RequestParam String toAccount,
            @RequestParam double amount) {
        
        log.info("接收到银行转账请求：从[{}]转账[{}]元到[{}]", fromAccount, amount, toAccount);
        
        Map<String, Object> result = new HashMap<>();
        try {
            boolean success = transactionService.basicTransactionDemo(fromAccount, toAccount, amount);
            
            result.put("success", success);
            result.put("message", success ? "转账成功" : "转账失败");
            result.put("fromAccount", fromAccount);
            result.put("toAccount", toAccount);
            result.put("amount", amount);
            
            log.info("银行转账操作完成，结果：{}", success ? "成功" : "失败");
            
        } catch (Exception e) {
            log.error("银行转账操作异常：{}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "转账异常：" + e.getMessage());
        }
        
        return result;
    }

    /**
     * 乐观锁事务演示：库存扣减
     * 
     * @param productId 商品ID
     * @param quantity 扣减数量
     * @return 扣减结果
     */
    @PostMapping("/stock/deduct")
    public Map<String, Object> deductStock(
            @RequestParam String productId,
            @RequestParam int quantity) {
        
        log.info("接收到库存扣减请求：商品[{}]扣减数量[{}]", productId, quantity);
        
        Map<String, Object> result = new HashMap<>();
        try {
            boolean success = transactionService.optimisticLockDemo(productId, quantity);
            
            result.put("success", success);
            result.put("message", success ? "库存扣减成功" : "库存扣减失败");
            result.put("productId", productId);
            result.put("quantity", quantity);
            
            log.info("库存扣减操作完成，结果：{}", success ? "成功" : "失败");
            
        } catch (Exception e) {
            log.error("库存扣减操作异常：{}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "库存扣减异常：" + e.getMessage());
        }
        
        return result;
    }

    /**
     * 事务回滚演示：积分变更
     * 
     * @param userId 用户ID
     * @param points 积分变化
     * @return 操作结果
     */
    @PostMapping("/points/update")
    public Map<String, Object> updatePoints(
            @RequestParam String userId,
            @RequestParam int points) {
        
        log.info("接收到积分变更请求：用户[{}]积分变化[{}]", userId, points);
        
        Map<String, Object> result = new HashMap<>();
        try {
            boolean success = transactionService.transactionRollbackDemo(userId, points);
            
            result.put("success", success);
            result.put("message", success ? "积分变更成功" : "积分变更失败或已回滚");
            result.put("userId", userId);
            result.put("points", points);
            
            log.info("积分变更操作完成，结果：{}", success ? "成功" : "失败或回滚");
            
        } catch (Exception e) {
            log.error("积分变更操作异常：{}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "积分变更异常：" + e.getMessage());
        }
        
        return result;
    }

    /**
     * 批量操作事务演示：批量更新用户状态
     * 
     * @param userIds 用户ID列表（逗号分隔）
     * @param status 新状态
     * @return 操作结果
     */
    @PostMapping("/users/batch-update")
    public Map<String, Object> batchUpdateUsers(
            @RequestParam String userIds,
            @RequestParam String status) {
        
        log.info("接收到批量用户状态更新请求：用户列表[{}]，新状态[{}]", userIds, status);
        
        Map<String, Object> result = new HashMap<>();
        try {
            List<String> userIdList = Arrays.asList(userIds.split(","));
            boolean success = transactionService.batchUpdateDemo(userIdList, status);
            
            result.put("success", success);
            result.put("message", success ? "批量更新成功" : "批量更新失败");
            result.put("userIds", userIdList);
            result.put("status", status);
            result.put("updateCount", userIdList.size());
            
            log.info("批量用户状态更新操作完成，结果：{}", success ? "成功" : "失败");
            
        } catch (Exception e) {
            log.error("批量用户状态更新操作异常：{}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "批量更新异常：" + e.getMessage());
        }
        
        return result;
    }

    /**
     * 综合事务演示：模拟订单处理
     * 包含多个相关操作的复杂事务场景
     * 
     * @param orderId 订单ID
     * @param userId 用户ID
     * @param productId 商品ID
     * @param quantity 购买数量
     * @param totalAmount 订单总金额
     * @return 处理结果
     */
    @PostMapping("/order/process")
    public Map<String, Object> processOrder(
            @RequestParam String orderId,
            @RequestParam String userId,
            @RequestParam String productId,
            @RequestParam int quantity,
            @RequestParam double totalAmount) {
        
        log.info("接收到订单处理请求：订单[{}]，用户[{}]，商品[{}]，数量[{}]，金额[{}]", 
                orderId, userId, productId, quantity, totalAmount);
        
        Map<String, Object> result = new HashMap<>();
        try {
            // 1. 先尝试扣减库存
            boolean stockDeducted = transactionService.optimisticLockDemo(productId, quantity);
            if (!stockDeducted) {
                result.put("success", false);
                result.put("message", "库存不足，订单处理失败");
                result.put("step", "库存扣减");
                log.warn("订单[{}]处理失败：库存不足", orderId);
                return result;
            }
            
            // 2. 模拟用户余额扣减（使用转账功能）
            boolean paymentDeducted = transactionService.basicTransactionDemo(
                    userId, "merchant_account", totalAmount);
            if (!paymentDeducted) {
                result.put("success", false);
                result.put("message", "支付失败，订单处理失败");
                result.put("step", "支付处理");
                log.warn("订单[{}]处理失败：支付失败", orderId);
                return result;
            }
            
            // 3. 批量更新订单相关状态
            List<String> statusUpdates = Arrays.asList(
                    "order_status:" + orderId,
                    "user_last_order:" + userId,
                    "product_last_sold:" + productId
            );
            transactionService.batchUpdateDemo(statusUpdates, "completed");
            
            result.put("success", true);
            result.put("message", "订单处理成功");
            result.put("orderId", orderId);
            result.put("userId", userId);
            result.put("productId", productId);
            result.put("quantity", quantity);
            result.put("totalAmount", totalAmount);
            result.put("steps", Arrays.asList("库存扣减", "支付处理", "状态更新"));
            
            log.info("订单[{}]处理成功", orderId);
            
        } catch (Exception e) {
            log.error("订单[{}]处理异常：{}", orderId, e.getMessage(), e);
            result.put("success", false);
            result.put("message", "订单处理异常：" + e.getMessage());
        }
        
        return result;
    }

    /**
     * 清理演示数据
     * 
     * @param prefix 数据前缀
     * @return 清理结果
     */
    @DeleteMapping("/cleanup")
    public Map<String, Object> cleanupData(@RequestParam(defaultValue = "account:,product:,user:,transfer:,stock:,points:,batch:,order_status:") String prefix) {
        log.info("接收到数据清理请求，前缀：{}", prefix);
        
        Map<String, Object> result = new HashMap<>();
        try {
            String[] prefixes = prefix.split(",");
            for (String p : prefixes) {
                transactionService.cleanupDemoData(p.trim());
            }
            
            result.put("success", true);
            result.put("message", "演示数据清理完成");
            result.put("cleanedPrefixes", Arrays.asList(prefixes));
            
            log.info("演示数据清理完成");
            
        } catch (Exception e) {
            log.error("数据清理异常：{}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "数据清理异常：" + e.getMessage());
        }
        
        return result;
    }

    /**
     * 获取Redis事务使用说明
     * 
     * @return 使用说明
     */
    @GetMapping("/help")
    public Map<String, Object> getTransactionHelp() {
        Map<String, Object> help = new HashMap<>();
        
        help.put("title", "Redis事务使用说明");
        help.put("description", "Redis事务通过MULTI/EXEC/DISCARD/WATCH命令实现原子性操作");
        
        Map<String, Object> features = new HashMap<>();
        features.put("原子性", "事务中的命令要么全部执行，要么全部不执行");
        features.put("一致性", "保证数据从一个一致性状态转换到另一个一致性状态");
        features.put("隔离性", "事务执行期间，其他客户端无法插入命令");
        features.put("持久性", "根据Redis的持久化配置决定");
        help.put("特性", features);
        
        Map<String, String> commands = new HashMap<>();
        commands.put("MULTI", "开始事务，后续命令将被放入队列");
        commands.put("EXEC", "执行事务队列中的所有命令");
        commands.put("DISCARD", "取消事务，清空命令队列");
        commands.put("WATCH", "监视键值变化，用于实现乐观锁");
        commands.put("UNWATCH", "取消对所有键的监视");
        help.put("核心命令", commands);
        
        Map<String, String> apis = new HashMap<>();
        apis.put("POST /redis/transaction/transfer", "银行转账演示（基础事务）");
        apis.put("POST /redis/transaction/stock/deduct", "库存扣减演示（乐观锁）");
        apis.put("POST /redis/transaction/points/update", "积分变更演示（事务回滚）");
        apis.put("POST /redis/transaction/users/batch-update", "批量更新演示");
        apis.put("POST /redis/transaction/order/process", "综合订单处理演示");
        apis.put("DELETE /redis/transaction/cleanup", "清理演示数据");
        help.put("可用接口", apis);
        
        return help;
    }
}
