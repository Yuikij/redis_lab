package com.soukon;

import com.soukon.datastructure.SkipList;
import com.soukon.datastructure.SkipListNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 跳表测试类
 * 
 * 测试跳表的各种功能，包括：
 * 1. 基本操作（插入、删除、查找）
 * 2. 边界条件测试
 * 3. 性能测试
 * 4. 数据一致性测试
 */
class SkipListTest {
    
    private SkipList<String> skipList;
    
    @BeforeEach
    void setUp() {
        skipList = new SkipList<>();
    }
    
    @Test
    void testInsertAndSearch() {
        // 测试插入和查找
        SkipListNode<String> node1 = skipList.insert(1.0, "Alice");
        SkipListNode<String> node2 = skipList.insert(2.0, "Bob");
        SkipListNode<String> node3 = skipList.insert(1.5, "Charlie");
        
        assertNotNull(node1);
        assertNotNull(node2);
        assertNotNull(node3);
        assertEquals(3, skipList.getLength());
        
        // 测试查找
        SkipListNode<String> found = skipList.search(1.0, "Alice");
        assertNotNull(found);
        assertEquals(1.0, found.score);
        assertEquals("Alice", found.obj);
        
        found = skipList.search(2.0, "Bob");
        assertNotNull(found);
        assertEquals(2.0, found.score);
        assertEquals("Bob", found.obj);
        
        // 测试查找不存在的元素
        found = skipList.search(3.0, "David");
        assertNull(found);
    }
    
    @Test
    void testDelete() {
        // 插入测试数据
        skipList.insert(1.0, "Alice");
        skipList.insert(2.0, "Bob");
        skipList.insert(3.0, "Charlie");
        assertEquals(3, skipList.getLength());
        
        // 删除存在的元素
        boolean deleted = skipList.delete(2.0, "Bob");
        assertTrue(deleted);
        assertEquals(2, skipList.getLength());
        
        // 验证删除成功
        SkipListNode<String> found = skipList.search(2.0, "Bob");
        assertNull(found);
        
        // 删除不存在的元素
        deleted = skipList.delete(4.0, "David");
        assertFalse(deleted);
        assertEquals(2, skipList.getLength());
    }
    
    @Test
    void testDuplicateInsert() {
        // 插入重复元素应该返回null
        SkipListNode<String> node1 = skipList.insert(1.0, "Alice");
        assertNotNull(node1);
        assertEquals(1, skipList.getLength());
        
        SkipListNode<String> node2 = skipList.insert(1.0, "Alice");
        assertNull(node2);
        assertEquals(1, skipList.getLength());
    }
    
    @Test
    void testRankQuery() {
        // 插入有序数据
        skipList.insert(10.0, "First");
        skipList.insert(20.0, "Second");
        skipList.insert(30.0, "Third");
        skipList.insert(40.0, "Fourth");
        skipList.insert(50.0, "Fifth");
        
        // 测试按排名查询
        SkipListNode<String> node = skipList.getByRank(1);
        assertNotNull(node);
        assertEquals(10.0, node.score);
        assertEquals("First", node.obj);
        
        node = skipList.getByRank(3);
        assertNotNull(node);
        assertEquals(30.0, node.score);
        assertEquals("Third", node.obj);
        
        node = skipList.getByRank(5);
        assertNotNull(node);
        assertEquals(50.0, node.score);
        assertEquals("Fifth", node.obj);
        
        // 测试边界情况
        node = skipList.getByRank(0);
        assertNull(node);
        
        node = skipList.getByRank(6);
        assertNull(node);
    }
    
    @Test
    void testScoreRangeQuery() {
        // 插入测试数据
        skipList.insert(1.0, "A");
        skipList.insert(2.5, "B");
        skipList.insert(3.2, "C");
        skipList.insert(4.8, "D");
        skipList.insert(6.1, "E");
        skipList.insert(7.3, "F");
        
        // 测试范围查询
        List<SkipListNode<String>> result = skipList.getByScoreRange(2.0, 5.0);
        assertEquals(3, result.size());
        
        // 验证结果按分值排序
        assertEquals(2.5, result.get(0).score);
        assertEquals("B", result.get(0).obj);
        assertEquals(3.2, result.get(1).score);
        assertEquals("C", result.get(1).obj);
        assertEquals(4.8, result.get(2).score);
        assertEquals("D", result.get(2).obj);
        
        // 测试空范围
        result = skipList.getByScoreRange(10.0, 20.0);
        assertTrue(result.isEmpty());
        
        // 测试包含所有元素的范围
        result = skipList.getByScoreRange(0.0, 10.0);
        assertEquals(6, result.size());
    }
    
    @Test
    void testEmptySkipList() {
        assertTrue(skipList.isEmpty());
        assertEquals(0, skipList.getLength());
        assertEquals(1, skipList.getLevel());
        
        assertNull(skipList.getFirst());
        assertNull(skipList.getLast());
        assertNull(skipList.getByRank(1));
        assertNull(skipList.search(1.0, "test"));
        
        assertTrue(skipList.getByScoreRange(0.0, 10.0).isEmpty());
    }
    
    @Test
    void testSingleElement() {
        SkipListNode<String> node = skipList.insert(5.0, "OnlyOne");
        assertNotNull(node);
        
        assertFalse(skipList.isEmpty());
        assertEquals(1, skipList.getLength());
        
        assertEquals(node, skipList.getFirst());
        assertEquals(node, skipList.getLast());
        assertEquals(node, skipList.getByRank(1));
        assertEquals(node, skipList.search(5.0, "OnlyOne"));
        
        List<SkipListNode<String>> range = skipList.getByScoreRange(0.0, 10.0);
        assertEquals(1, range.size());
        assertEquals(node, range.get(0));
    }
    
    @Test
    void testOrderedInsertion() {
        // 按顺序插入
        skipList.insert(1.0, "A");
        skipList.insert(2.0, "B");
        skipList.insert(3.0, "C");
        skipList.insert(4.0, "D");
        skipList.insert(5.0, "E");
        
        // 验证顺序
        for (int i = 1; i <= 5; i++) {
            SkipListNode<String> node = skipList.getByRank(i);
            assertNotNull(node);
            assertEquals(i, node.score);
        }
    }
    
    @Test
    void testReverseOrderInsertion() {
        // 按逆序插入
        skipList.insert(5.0, "E");
        skipList.insert(4.0, "D");
        skipList.insert(3.0, "C");
        skipList.insert(2.0, "B");
        skipList.insert(1.0, "A");
        
        // 验证顺序（跳表会自动排序）
        for (int i = 1; i <= 5; i++) {
            SkipListNode<String> node = skipList.getByRank(i);
            assertNotNull(node);
            assertEquals(i, node.score);
        }
    }
    
    @Test
    void testRandomInsertion() {
        // 随机插入
        double[] scores = {3.5, 1.2, 4.8, 2.1, 5.9, 0.7, 3.1};
        String[] values = {"G", "B", "H", "C", "I", "A", "F"};
        
        for (int i = 0; i < scores.length; i++) {
            skipList.insert(scores[i], values[i]);
        }
        
        assertEquals(7, skipList.getLength());
        
        // 验证第一个和最后一个元素
        SkipListNode<String> first = skipList.getFirst();
        SkipListNode<String> last = skipList.getLast();
        
        assertNotNull(first);
        assertNotNull(last);
        assertEquals(0.7, first.score);
        assertEquals("A", first.obj);
        assertEquals(5.9, last.score);
        assertEquals("I", last.obj);
    }
    
    @Test
    void testBackwardLinks() {
        // 插入数据
        skipList.insert(1.0, "A");
        skipList.insert(2.0, "B");
        skipList.insert(3.0, "C");
        
        // 从尾部向前遍历
        SkipListNode<String> current = skipList.getLast();
        int count = 0;
        while (current != null) {
            count++;
            current = current.backward;
        }
        
        assertEquals(3, count);
    }
    
    @Test
    void testPerformanceWithLargeDataset() {
        int size = 1000;
        
        // 插入大量数据
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < size; i++) {
            skipList.insert(Math.random() * 1000, "Element" + i);
        }
        long insertTime = System.currentTimeMillis() - startTime;
        
        assertEquals(size, skipList.getLength());
        
        // 性能应该是合理的（这里只是确保不会超时）
        assertTrue(insertTime < 1000, "插入操作耗时过长: " + insertTime + "ms");
        
        // 测试查找性能
        startTime = System.currentTimeMillis();
        for (int i = 0; i < 100; i++) {
            skipList.getByRank((long)(Math.random() * size) + 1);
        }
        long searchTime = System.currentTimeMillis() - startTime;
        
        assertTrue(searchTime < 100, "查找操作耗时过长: " + searchTime + "ms");
    }
    
    @Test
    void testSameScoreDifferentObjects() {
        // 测试相同分值但不同对象的情况
        skipList.insert(1.0, "Alice");
        skipList.insert(1.0, "Bob");
        skipList.insert(1.0, "Charlie");
        
        assertEquals(3, skipList.getLength());
        
        // 所有元素都应该能找到
        assertNotNull(skipList.search(1.0, "Alice"));
        assertNotNull(skipList.search(1.0, "Bob"));
        assertNotNull(skipList.search(1.0, "Charlie"));
        
        // 范围查询应该返回所有元素
        List<SkipListNode<String>> result = skipList.getByScoreRange(1.0, 1.0);
        assertEquals(3, result.size());
    }
    
    @Test
    void testDeleteFromMiddle() {
        // 插入多个元素
        skipList.insert(1.0, "A");
        skipList.insert(2.0, "B");
        skipList.insert(3.0, "C");
        skipList.insert(4.0, "D");
        skipList.insert(5.0, "E");
        
        // 删除中间元素
        assertTrue(skipList.delete(3.0, "C"));
        assertEquals(4, skipList.getLength());
        
        // 验证删除后的顺序
        SkipListNode<String> node = skipList.getByRank(1);
        assertEquals("A", node.obj);
        
        node = skipList.getByRank(2);
        assertEquals("B", node.obj);
        
        node = skipList.getByRank(3);
        assertEquals("D", node.obj);
        
        node = skipList.getByRank(4);
        assertEquals("E", node.obj);
    }
}
