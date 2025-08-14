package com.soukon;

import com.soukon.service.RedisUseCases;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Distance;
import org.springframework.data.redis.connection.stream.MapRecord;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RedisUseCasesTest {

    @BeforeAll
    static void checkRedis(@org.springframework.beans.factory.annotation.Autowired org.springframework.core.env.Environment env) {
        RedisConnectivity.assumeRedis(env);
    }

    @Autowired
    private RedisUseCases redisUseCases;

    @Test
    void testStringExamples() {
        String key = "usecases:string:" + UUID.randomUUID();
        String val = redisUseCases.stringExamples(key);
        assertEquals("v1", val, "字符串写入/读取应为 v1");
    }

    @Test
    void testHashExamples() {
        String userId = "u-" + UUID.randomUUID();
        Map<Object, Object> map = redisUseCases.hashExamples(userId);
        assertEquals("Alice", String.valueOf(map.get("name")), "Hash 字段 name 应为 Alice");
        assertEquals("3", String.valueOf(map.get("level")), "Hash 字段 level 应为 3");
        assertEquals("10", String.valueOf(map.get("score")), "Hash 字段 score 应为 10");
    }

    @Test
    void testListExamples() {
        String listKey = "usecases:list:" + UUID.randomUUID();
        List<String> list = redisUseCases.listExamples(listKey);
        assertEquals(3, list.size(), "List 长度应为 3");
        assertTrue(list.containsAll(List.of("job1", "job2", "job3")), "List 应包含写入的三个元素");
    }

    @Test
    void testSetExamples() {
        String setKey = "usecases:set:" + UUID.randomUUID();
        Set<String> set = redisUseCases.setExamples(setKey);
        assertEquals(3, set.size(), "Set 去重后长度应为 3");
        assertTrue(set.containsAll(Set.of("java", "redis", "spring")), "Set 应包含写入的标签");
    }

    @Test
    void testZsetExamples() {
        String zsetKey = "usecases:zset:" + UUID.randomUUID();
        Set<String> top = redisUseCases.zsetExamples(zsetKey);
        assertEquals(3, top.size(), "ZSet TopN 结果应为 3");
        String first = top.iterator().next();
        assertEquals("u2", first, "ZSet 第一名应为 u2（分数 120）");
    }

    @Test
    void testBitmapExamples() {
        String bitmapKey = "usecases:bitmap:" + UUID.randomUUID();
        boolean ok = redisUseCases.bitmapExamples(bitmapKey, 5);
        assertTrue(ok, "位图第 5 位应被设置为 1");
    }

    @Test
    void testHyperLogLogExamples() {
        String hllKey = "usecases:hll:" + UUID.randomUUID();
        Long size = redisUseCases.hyperLogLogExamples(hllKey);
        assertEquals(3L, size, "HyperLogLog 估算去重数量应为 3");
    }

    @Test
    void testGeoExamples() {
        String geoKey = "usecases:geo:" + UUID.randomUUID();
        Distance d = redisUseCases.geoExamples(geoKey);
        assertNotNull(d, "GEO 距离不应为 null");
        assertTrue(d.getValue() > 0d, "北京到上海的距离应大于 0");
    }

    @Test
    void testStreamExamples() {
        String streamKey = "usecases:stream:" + UUID.randomUUID();
        List<MapRecord<String, Object, Object>> records = redisUseCases.streamExamples(streamKey);
        assertEquals(1, records.size(), "Stream 新建键应仅有 1 条记录");
        Map<Object, Object> payload = records.get(0).getValue();
        assertEquals("1001", String.valueOf(payload.get("orderId")), "订单号应为 1001");
        assertEquals("88.8", String.valueOf(payload.get("amount")), "订单金额应为 88.8");
    }
}


