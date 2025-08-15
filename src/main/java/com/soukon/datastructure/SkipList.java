package com.soukon.datastructure;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Redis跳表的Java实现
 * 
 * 跳表是一种概率性数据结构，通过多层索引实现快速查找、插入和删除操作
 * Redis在有序集合(ZSET)中使用跳表来维护元素的有序性
 * 
 * 特点：
 * 1. 平均时间复杂度：O(log n)
 * 2. 空间复杂度：O(n)
 * 3. 支持范围查询
 * 4. 实现简单，性能稳定
 * 
 * @param <T> 存储的对象类型
 */
public class SkipList<T> {
    
    /**
     * 最大层级数，Redis中通常设置为32或64
     */
    private static final int SKIPLIST_MAXLEVEL = 32;
    
    /**
     * 概率因子，决定节点层数的概率分布
     * Redis中设置为0.25，即1/4的概率提升到下一层
     */
    private static final double SKIPLIST_P = 0.25;
    
    /**
     * 头节点，不存储实际数据
     */
    private final SkipListNode<T> header;
    
    /**
     * 尾节点指针
     */
    private SkipListNode<T> tail;
    
    /**
     * 跳表当前最高层级
     */
    private int level;
    
    /**
     * 跳表节点数量
     */
    private long length;
    
    /**
     * 随机数生成器
     */
    private final Random random;
    
    /**
     * 构造函数
     */
    public SkipList() {
        this.random = new Random();
        this.level = 1;
        this.length = 0;
        
        // 创建头节点，层级为最大值
        this.header = new SkipListNode<>(SKIPLIST_MAXLEVEL, 0, null);
        this.tail = null;
    }
    
    /**
     * 随机生成节点层数
     * 使用概率算法，模拟Redis的实现
     * 
     * @return 节点层数 (1 到 SKIPLIST_MAXLEVEL)
     */
    private int randomLevel() {
        int level = 1;
        while (random.nextDouble() < SKIPLIST_P && level < SKIPLIST_MAXLEVEL) {
            level++;
        }
        return level;
    }
    
    /**
     * 插入节点
     * 
     * @param score 分值
     * @param obj 对象
     * @return 插入的节点，如果已存在则返回null
     */
    @SuppressWarnings("unchecked")
    public SkipListNode<T> insert(double score, T obj) {
        SkipListNode<T>[] update = new SkipListNode[SKIPLIST_MAXLEVEL];
        long[] rank = new long[SKIPLIST_MAXLEVEL];
        SkipListNode<T> x = header;
        
        // 从最高层开始查找插入位置
        for (int i = level - 1; i >= 0; i--) {
            rank[i] = (i == level - 1) ? 0 : rank[i + 1];
            
            while (x.level[i].forward != null &&
                   (x.level[i].forward.score < score ||
                    (x.level[i].forward.score == score && 
                     shouldInsertAfter((T)x.level[i].forward.obj, obj)))) {
                rank[i] += x.level[i].span;
                x = (SkipListNode<T>) x.level[i].forward;
            }
            update[i] = x;
        }
        
        // 检查是否已存在相同score和obj的节点
        x = (SkipListNode<T>) x.level[0].forward;
        if (x != null && x.score == score && isEqual(x.obj, obj)) {
            return null; // 节点已存在
        }
        
        // 生成新节点的层数
        int newLevel = randomLevel();
        
        // 如果新节点层数超过当前最高层，需要更新header的相关层
        if (newLevel > level) {
            for (int i = level; i < newLevel; i++) {
                rank[i] = 0;
                update[i] = header;
                update[i].level[i].span = length;
            }
            level = newLevel;
        }
        
        // 创建新节点
        x = new SkipListNode<>(newLevel, score, obj);
        
        // 更新指针和跨度
        for (int i = 0; i < newLevel; i++) {
            x.level[i].forward = update[i].level[i].forward;
            update[i].level[i].forward = x;
            
            // 更新跨度
            x.level[i].span = update[i].level[i].span - (rank[0] - rank[i]);
            update[i].level[i].span = (rank[0] - rank[i]) + 1;
        }
        
        // 增加未触及层级的跨度
        for (int i = newLevel; i < level; i++) {
            update[i].level[i].span++;
        }
        
        // 设置后退指针
        x.backward = (update[0] == header) ? null : update[0];
        if (x.level[0].forward != null) {
            ((SkipListNode<T>) x.level[0].forward).backward = x;
        } else {
            tail = x;
        }
        
        length++;
        return x;
    }
    
    /**
     * 删除节点
     * 
     * @param score 分值
     * @param obj 对象
     * @return 是否删除成功
     */
    @SuppressWarnings("unchecked")
    public boolean delete(double score, T obj) {
        SkipListNode<T>[] update = new SkipListNode[SKIPLIST_MAXLEVEL];
        SkipListNode<T> x = header;
        
        // 查找待删除节点
        for (int i = level - 1; i >= 0; i--) {
            while (x.level[i].forward != null &&
                   (x.level[i].forward.score < score ||
                    (x.level[i].forward.score == score && 
                     shouldInsertAfter((T)x.level[i].forward.obj, obj)))) {
                x = (SkipListNode<T>) x.level[i].forward;
            }
            update[i] = x;
        }
        
        x = (SkipListNode<T>) x.level[0].forward;
        if (x != null && x.score == score && isEqual(x.obj, obj)) {
            deleteNode(x, update);
            return true;
        }
        
        return false;
    }
    
    /**
     * 内部删除节点方法
     */
    private void deleteNode(SkipListNode<T> x, SkipListNode<T>[] update) {
        for (int i = 0; i < level; i++) {
            if (update[i].level[i].forward == x) {
                update[i].level[i].span += x.level[i].span - 1;
                update[i].level[i].forward = x.level[i].forward;
            } else {
                update[i].level[i].span--;
            }
        }
        
        if (x.level[0].forward != null) {
            @SuppressWarnings("unchecked")
            SkipListNode<T> next = (SkipListNode<T>) x.level[0].forward;
            next.backward = x.backward;
        } else {
            tail = x.backward;
        }
        
        // 更新level
        while (level > 1 && header.level[level - 1].forward == null) {
            level--;
        }
        
        length--;
    }
    
    /**
     * 查找节点
     * 
     * @param score 分值
     * @param obj 对象
     * @return 找到的节点，未找到返回null
     */
    @SuppressWarnings("unchecked")
    public SkipListNode<T> search(double score, T obj) {
        SkipListNode<T> x = header;
        
        for (int i = level - 1; i >= 0; i--) {
            while (x.level[i].forward != null &&
                   (x.level[i].forward.score < score ||
                    (x.level[i].forward.score == score && 
                     shouldInsertAfter((T)x.level[i].forward.obj, obj)))) {
                x = (SkipListNode<T>) x.level[i].forward;
            }
        }
        
        x = (SkipListNode<T>) x.level[0].forward;
        if (x != null && x.score == score && isEqual(x.obj, obj)) {
            return x;
        }
        
        return null;
    }
    
    /**
     * 根据排名获取节点
     * 
     * @param rank 排名（从1开始）
     * @return 对应排名的节点
     */
    @SuppressWarnings("unchecked")
    public SkipListNode<T> getByRank(long rank) {
        if (rank <= 0 || rank > length) {
            return null;
        }
        
        SkipListNode<T> x = header;
        long traversed = 0;
        
        for (int i = level - 1; i >= 0; i--) {
            while (x.level[i].forward != null && 
                   (traversed + x.level[i].span) < rank) {
                traversed += x.level[i].span;
                x = (SkipListNode<T>) x.level[i].forward;
            }
        }
        
        if (traversed + 1 == rank && x.level[0].forward != null) {
            return (SkipListNode<T>) x.level[0].forward;
        }
        
        return null;
    }
    
    /**
     * 获取指定分值范围内的节点
     * 
     * @param minScore 最小分值
     * @param maxScore 最大分值
     * @return 范围内的节点列表
     */
    @SuppressWarnings("unchecked")
    public List<SkipListNode<T>> getByScoreRange(double minScore, double maxScore) {
        List<SkipListNode<T>> result = new ArrayList<>();
        SkipListNode<T> x = header;
        
        // 找到第一个分值 >= minScore的节点
        for (int i = level - 1; i >= 0; i--) {
            while (x.level[i].forward != null && 
                   x.level[i].forward.score < minScore) {
                x = (SkipListNode<T>) x.level[i].forward;
            }
        }
        
        x = (SkipListNode<T>) x.level[0].forward;
        
        // 收集范围内的节点
        while (x != null && x.score <= maxScore) {
            result.add(x);
            x = (SkipListNode<T>) x.level[0].forward;
        }
        
        return result;
    }
    
    /**
     * 获取跳表长度
     * 
     * @return 节点数量
     */
    public long getLength() {
        return length;
    }
    
    /**
     * 获取当前最高层级
     * 
     * @return 最高层级
     */
    public int getLevel() {
        return level;
    }
    
    /**
     * 判断是否为空
     * 
     * @return 是否为空
     */
    public boolean isEmpty() {
        return length == 0;
    }
    
    /**
     * 获取第一个节点
     * 
     * @return 第一个节点
     */
    @SuppressWarnings("unchecked")
    public SkipListNode<T> getFirst() {
        return (SkipListNode<T>) header.level[0].forward;
    }
    
    /**
     * 获取最后一个节点
     * 
     * @return 最后一个节点
     */
    public SkipListNode<T> getLast() {
        return tail;
    }
    
    /**
     * 比较两个对象是否相等
     */
    private boolean isEqual(T obj1, T obj2) {
        if (obj1 == null && obj2 == null) return true;
        if (obj1 == null || obj2 == null) return false;
        return obj1.equals(obj2);
    }
    
    /**
     * 决定是否应该在指定对象之后插入新对象
     * 这里简单使用字符串比较，实际Redis中有更复杂的比较逻辑
     */
    private boolean shouldInsertAfter(T existing, T newObj) {
        if (existing == null && newObj == null) return false;
        if (existing == null) return false;
        if (newObj == null) return true;
        
        // 使用字符串比较作为示例
        return existing.toString().compareTo(newObj.toString()) < 0;
    }
    
    /**
     * 打印跳表结构（用于调试）
     */
    @SuppressWarnings("unchecked")
    public void printStructure() {
        System.out.println("SkipList Structure (length=" + length + ", level=" + level + "):");
        
        for (int i = level - 1; i >= 0; i--) {
            System.out.print("Level " + (i + 1) + ": ");
            SkipListNode<T> x = (SkipListNode<T>) header.level[i].forward;
            while (x != null) {
                System.out.printf("%.1f:%s(span=%d) -> ", 
                                x.score, x.obj, x.level[i].span);
                x = (SkipListNode<T>) x.level[i].forward;
            }
            System.out.println("NULL");
        }
        System.out.println();
    }
}