package com.soukon.datastructure;

/**
 * 跳表节点
 * Redis跳表节点的Java实现，每个节点包含分值、对象、后退指针和层级数组
 * 
 * @param <T> 存储的对象类型
 */
public class SkipListNode<T> {
    
    /**
     * 分值，用于排序
     */
    public double score;
    
    /**
     * 存储的对象
     */
    public T obj;
    
    /**
     * 后退指针，用于从尾部向头部遍历
     */
    public SkipListNode<T> backward;
    
    /**
     * 层级数组，每一层包含前进指针和跨度
     */
    public Level[] level;
    
    /**
     * 跳表层级结构
     */
    public static class Level {
        /**
         * 前进指针，指向该层的下一个节点
         */
        public SkipListNode<?> forward;
        
        /**
         * 跨度，用于计算排名
         */
        public long span;
        
        public Level() {
            this.forward = null;
            this.span = 0;
        }
    }
    
    /**
     * 构造函数
     * 
     * @param level 节点层数
     * @param score 分值
     * @param obj 存储对象
     */
    public SkipListNode(int level, double score, T obj) {
        this.score = score;
        this.obj = obj;
        this.backward = null;
        this.level = new Level[level];
        
        // 初始化每一层
        for (int i = 0; i < level; i++) {
            this.level[i] = new Level();
        }
    }
    
    @Override
    public String toString() {
        return String.format("SkipListNode{score=%.2f, obj=%s, levels=%d}", 
                           score, obj, level.length);
    }
}
