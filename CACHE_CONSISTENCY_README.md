# Redis 缓存与数据库一致性策略实现

本项目演示了四种常见的Redis缓存与数据库一致性策略的完整实现，包括详细的中文注释和日志输出。

## 📋 目录

- [项目概述](#项目概述)
- [技术栈](#技术栈)
- [一致性策略](#一致性策略)
- [项目结构](#项目结构)
- [快速开始](#快速开始)
- [API接口](#api接口)
- [测试用例](#测试用例)
- [性能对比](#性能对比)
- [最佳实践](#最佳实践)

## 🎯 项目概述

缓存与数据库的数据一致性是分布式系统中的经典问题。本项目通过实际代码演示了四种主流解决方案：

1. **Cache Aside（旁路缓存）** - 最常用的策略
2. **Write Through（写穿透）** - 强一致性策略
3. **Write Behind（写回）** - 高性能策略
4. **Delayed Double Delete（延时双删）** - 解决并发问题的策略

每种策略都有完整的实现、详细的中文注释和日志输出，便于学习和理解。

## 🛠 技术栈

- **Spring Boot 3.2.5** - 应用框架
- **Spring Data JPA** - 数据访问层
- **H2 Database** - 内存数据库（模拟真实数据库）
- **Spring Data Redis** - Redis操作
- **Lettuce** - Redis客户端
- **Jackson** - JSON序列化
- **JUnit 5** - 单元测试
- **Lombok** - 简化代码

## 📊 一致性策略

### 1. Cache Aside（旁路缓存）

**原理**：应用程序直接管理缓存
- **读取**：先查缓存 → 缓存miss时查数据库 → 将结果写入缓存
- **更新**：先更新数据库 → 删除缓存
- **删除**：先删除数据库 → 删除缓存

**优点**：
- 实现简单，容错性好
- 缓存故障不影响数据库操作
- 适用于读多写少的场景

**缺点**：
- 可能存在短暂的数据不一致
- 缓存穿透问题

```java
// 查询示例
public Optional<User> getUserById(Long id) {
    // 1. 先查缓存
    String cached = redisTemplate.opsForValue().get("user:" + id);
    if (cached != null) {
        return Optional.of(deserialize(cached));
    }
    
    // 2. 查数据库
    Optional<User> user = userRepository.findById(id);
    
    // 3. 写入缓存
    if (user.isPresent()) {
        redisTemplate.opsForValue().set("user:" + id, serialize(user.get()));
    }
    
    return user;
}
```

### 2. Write Through（写穿透）

**原理**：缓存作为主要的数据访问层
- **读取**：先查缓存 → 缓存miss时查数据库并写入缓存
- **写入**：同时写入缓存和数据库

**优点**：
- 数据一致性强
- 读取性能好
- 简化应用逻辑

**缺点**：
- 写入性能较差（需要写两个地方）
- 缓存故障影响写入操作
- 浪费存储空间（所有数据都缓存）

```java
// 更新示例
@Transactional
public User updateUser(User user) {
    // 1. 更新数据库
    User updated = userRepository.save(user);
    
    // 2. 立即更新缓存
    String json = objectMapper.writeValueAsString(updated);
    redisTemplate.opsForValue().set("user:" + user.getId(), json);
    
    return updated;
}
```

### 3. Write Behind（写回）

**原理**：优先写缓存，异步写数据库
- **读取**：先查缓存 → 缓存miss时查数据库并写入缓存
- **写入**：立即写缓存 → 标记脏数据 → 异步批量写数据库

**优点**：
- 写入性能最好
- 减少数据库压力
- 支持批量操作优化

**缺点**：
- 可能有数据丢失风险
- 一致性相对较弱
- 实现复杂度高

```java
// 异步更新示例
@Async
public CompletableFuture<User> updateUser(User user) {
    // 1. 立即更新缓存
    String json = objectMapper.writeValueAsString(user);
    redisTemplate.opsForValue().set("user:" + user.getId(), json);
    
    // 2. 标记为脏数据
    redisTemplate.opsForSet().add("dirty_users", user.getId().toString());
    
    // 3. 异步更新数据库
    return asyncUpdateDatabase(user);
}

// 定时批量同步
@Scheduled(fixedDelay = 30000)
public void syncDirtyData() {
    Set<String> dirtyIds = redisTemplate.opsForSet().members("dirty_users");
    // 批量同步到数据库...
}
```

### 4. Delayed Double Delete（延时双删）

**原理**：通过两次删除缓存解决并发读写问题
- **更新**：删除缓存 → 更新数据库 → 延时删除缓存
- **删除**：删除缓存 → 删除数据库 → 延时删除缓存

**优点**：
- 有效解决读写并发导致的数据不一致
- 实现相对简单
- 适用于高并发场景

**缺点**：
- 延时期间可能读到旧数据
- 需要合理设置延时时间
- 增加了系统复杂性

```java
// 延时双删示例
@Transactional
public User updateUser(User user) {
    String cacheKey = "user:" + user.getId();
    
    // 第1次删除缓存
    redisTemplate.delete(cacheKey);
    
    // 更新数据库
    User updated = userRepository.save(user);
    
    // 异步延时删除缓存
    asyncDelayedDelete(cacheKey);
    
    return updated;
}

@Async
public void asyncDelayedDelete(String cacheKey) {
    Thread.sleep(1000); // 延时1秒
    redisTemplate.delete(cacheKey); // 第2次删除
}
```

## 📁 项目结构

```
src/main/java/com/soukon/
├── entity/
│   └── User.java                    # 用户实体类
├── repository/
│   └── UserRepository.java         # 数据访问接口
├── service/
│   ├── CacheAsideService.java      # Cache Aside实现
│   ├── WriteThroughService.java    # Write Through实现
│   ├── WriteBehindService.java     # Write Behind实现
│   └── DelayedDoubleDeleteService.java # 延时双删实现
├── controller/
│   └── CacheConsistencyController.java # REST API控制器
├── config/
│   └── AsyncConfig.java            # 异步配置
└── ...

src/test/java/com/soukon/
└── CacheConsistencyTest.java       # 综合测试类
```

## 🚀 快速开始

### 1. 环境准备

确保已安装：
- JDK 17+
- Maven 3.6+
- Redis 服务器

### 2. 克隆项目

```bash
git clone <repository-url>
cd redis_lab
```

### 3. 配置Redis

修改 `src/main/resources/application.yml`：

```yaml
spring:
  data:
    redis:
      host: localhost  # 修改为你的Redis地址
      port: 6379
      database: 0
```

### 4. 运行项目

```bash
# 编译项目
mvn clean compile

# 运行测试
mvn test

# 启动应用
mvn spring-boot:run
```

### 5. 访问应用

- **应用地址**：http://localhost:8080
- **H2控制台**：http://localhost:8080/h2-console
- **API文档**：查看下方API接口说明

## 🔌 API接口

### Cache Aside 模式

```http
# 创建用户
POST /api/cache-consistency/cache-aside/users
Content-Type: application/json
{
  "username": "test_user",
  "email": "test@example.com",
  "nickname": "测试用户",
  "age": 25
}

# 查询用户
GET /api/cache-consistency/cache-aside/users/{id}

# 更新用户
PUT /api/cache-consistency/cache-aside/users/{id}
Content-Type: application/json
{
  "username": "updated_user",
  "email": "updated@example.com",
  "nickname": "更新用户",
  "age": 30
}

# 删除用户
DELETE /api/cache-consistency/cache-aside/users/{id}
```

### Write Through 模式

```http
# 创建用户
POST /api/cache-consistency/write-through/users

# 查询用户
GET /api/cache-consistency/write-through/users/{id}

# 更新用户
PUT /api/cache-consistency/write-through/users/{id}

# 批量更新年龄
PUT /api/cache-consistency/write-through/users/{id}/age?age=35

# 检查一致性
GET /api/cache-consistency/write-through/users/{id}/consistency
```

### Write Behind 模式

```http
# 创建用户（异步）
POST /api/cache-consistency/write-behind/users

# 查询用户
GET /api/cache-consistency/write-behind/users/{id}

# 更新用户（异步）
PUT /api/cache-consistency/write-behind/users/{id}

# 获取脏数据数量
GET /api/cache-consistency/write-behind/dirty-count

# 强制刷新脏数据
POST /api/cache-consistency/write-behind/flush

# 检查是否为脏数据
GET /api/cache-consistency/write-behind/users/{id}/dirty
```

### 延时双删策略

```http
# 创建用户
POST /api/cache-consistency/delayed-double-delete/users

# 查询用户
GET /api/cache-consistency/delayed-double-delete/users/{id}

# 更新用户（触发延时双删）
PUT /api/cache-consistency/delayed-double-delete/users/{id}

# 批量更新年龄
PUT /api/cache-consistency/delayed-double-delete/users/{id}/age?age=35

# 检查缓存是否存在
GET /api/cache-consistency/delayed-double-delete/users/{id}/cache-exists

# 缓存预热
POST /api/cache-consistency/delayed-double-delete/users/{id}/warmup

# 手动清除缓存
DELETE /api/cache-consistency/delayed-double-delete/users/{id}/cache
```

### 通用接口

```http
# 获取所有策略说明
GET /api/cache-consistency/strategies

# 健康检查
GET /api/cache-consistency/health
```

## 🧪 测试用例

项目包含完整的测试用例，涵盖：

1. **功能测试**：每种策略的基本CRUD操作
2. **一致性测试**：验证缓存与数据库数据一致性
3. **并发测试**：模拟高并发场景下的数据一致性
4. **性能测试**：对比不同策略的性能表现
5. **异常测试**：测试异常情况下的降级机制

运行测试：

```bash
# 运行所有测试
mvn test

# 运行特定测试类
mvn test -Dtest=CacheConsistencyTest

# 运行特定测试方法
mvn test -Dtest=CacheConsistencyTest#testCacheAsidePattern
```

## 📈 性能对比

基于测试结果的性能对比（仅供参考）：

| 策略 | 读取性能 | 写入性能 | 一致性 | 复杂度 | 适用场景 |
|------|----------|----------|--------|--------|----------|
| Cache Aside | ⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐ | 读多写少 |
| Write Through | ⭐⭐⭐⭐⭐ | ⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | 强一致性要求 |
| Write Behind | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐ | ⭐⭐⭐⭐ | 高并发写入 |
| Delayed Double Delete | ⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | 解决并发问题 |

## 💡 最佳实践

### 1. 选择合适的策略

- **读多写少**：选择 Cache Aside
- **强一致性要求**：选择 Write Through
- **高并发写入**：选择 Write Behind
- **解决并发问题**：选择 Delayed Double Delete

### 2. 缓存设计原则

- **设置合理的TTL**：防止缓存永久占用内存
- **使用合理的Key设计**：便于管理和清理
- **监控缓存命中率**：优化缓存策略
- **设置合理的超时时间**：防止操作阻塞

### 3. 异常处理

- **缓存降级**：缓存不可用时降级到数据库
- **重试机制**：临时性错误的重试处理
- **日志监控**：记录关键操作和异常情况
- **告警机制**：及时发现和处理问题

### 4. 性能优化

- **批量操作**：减少网络往返次数
- **连接池配置**：合理配置Redis连接池
- **序列化优化**：选择高效的序列化方式
- **异步处理**：非关键路径使用异步操作

### 5. 监控指标

- **缓存命中率**：监控缓存效果
- **响应时间**：监控系统性能
- **错误率**：监控系统稳定性
- **内存使用量**：监控缓存内存占用

## 📝 日志说明

项目中的日志按照操作类型和阶段进行分类：

- **`【策略名 操作】`**：标识具体的缓存策略和操作类型
- **`✅`**：表示操作成功
- **`⚠️`**：表示警告信息
- **`❌`**：表示操作失败
- **详细的中文描述**：说明操作的具体内容和结果

示例日志：
```
【Cache Aside 读取】开始查询用户ID: 1
【Cache Aside 读取】缓存未命中，查询数据库...
【Cache Aside 读取】数据库查询成功，已写入缓存，用户: test_user
✅ 1. 用户创建成功，ID: 1
```

## 🤝 贡献指南

欢迎提交Issue和Pull Request来改进项目！

## 📄 许可证

本项目采用MIT许可证，详见LICENSE文件。
