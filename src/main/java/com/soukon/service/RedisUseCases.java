package com.soukon.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Range;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 使用基于 Lettuce 的 {@link StringRedisTemplate} 演示 Redis 常见数据结构的读写。
 * 方法尽量保持"小而全"，便于独立调用与测试。
 */
@Service
public class RedisUseCases {

	private static final Logger log = LoggerFactory.getLogger(RedisUseCases.class);

	private final StringRedisTemplate lettuceStringRedisTemplate;

	public RedisUseCases(@Qualifier("lettuceStringRedisTemplate") StringRedisTemplate lettuceStringRedisTemplate) {
		this.lettuceStringRedisTemplate = lettuceStringRedisTemplate;
	}

	/**
	 * String：KV 缓存（含过期时间）与简单计数器示例。
	 */
	public String stringExamples(String key) {
		lettuceStringRedisTemplate.opsForValue().set(key, "v1", Duration.ofMinutes(5));
		log.info("写入字符串：key={} 值=v1，过期=5分钟", key);
		Long pv = lettuceStringRedisTemplate.opsForValue().increment("metrics:pv:home");
		log.info("页面 PV 计数自增：metrics:pv:home -> {}", pv);
		String val = lettuceStringRedisTemplate.opsForValue().get(key);
		log.info("读取字符串：key={} 值={}", key, val);
		return val;
	}

	/**
	 * Hash：对象字段存储与数值自增示例。
	 */
	public Map<Object, Object> hashExamples(String userId) {
		String key = "user:" + userId;
		lettuceStringRedisTemplate.opsForHash().put(key, "name", "Alice");
		lettuceStringRedisTemplate.opsForHash().put(key, "level", "3");
		lettuceStringRedisTemplate.opsForHash().increment(key, "score", 10);
		log.info("写入 Hash：key={} 字段：name=Alice, level=3, score+=10", key);
		return lettuceStringRedisTemplate.opsForHash().entries(key);
	}

	/**
	 * List：队列/时间线基础操作示例。
	 */
	public List<String> listExamples(String listKey) {
		lettuceStringRedisTemplate.opsForList().leftPushAll(listKey, "job1", "job2", "job3");
		log.info("写入 List：key={} 元素=[job1, job2, job3]（从左侧入队）", listKey);
		return lettuceStringRedisTemplate.opsForList().range(listKey, 0, -1);
	}

	/**
	 * Set：去重集合/标签示例。
	 */
	public Set<String> setExamples(String setKey) {
		lettuceStringRedisTemplate.opsForSet().add(setKey, "java", "redis", "spring", "redis");
		log.info("写入 Set：key={} 元素（自动去重）=[java, redis, spring, redis]", setKey);
		return lettuceStringRedisTemplate.opsForSet().members(setKey);
	}

	/**
	 * ZSet：排行榜 TopN 查询示例。
	 */
	public Set<String> zsetExamples(String zsetKey) {
		lettuceStringRedisTemplate.opsForZSet().add(zsetKey, "u1", 100);
		lettuceStringRedisTemplate.opsForZSet().add(zsetKey, "u2", 120);
		lettuceStringRedisTemplate.opsForZSet().add(zsetKey, "u3", 110);
		log.info("写入 ZSet：key={} (u1:100, u2:120, u3:110)；查询 Top3", zsetKey);
		return lettuceStringRedisTemplate.opsForZSet().reverseRangeByScore(zsetKey, 0, Double.MAX_VALUE, 0, 3);
	}

	/**
	 * Bitmap：签到/活跃标记示例。
	 */
	public boolean bitmapExamples(String bitmapKey, int dayIndex) {
		lettuceStringRedisTemplate.opsForValue().setBit(bitmapKey, dayIndex, true);
		Boolean bit = lettuceStringRedisTemplate.opsForValue().getBit(bitmapKey, dayIndex);
		log.info("位图：key={} 第{}位 置为1；读取结果={}", bitmapKey, dayIndex, Boolean.TRUE.equals(bit));
		return Boolean.TRUE.equals(bit);
	}

	/**
	 * HyperLogLog：去重统计（UV）示例。
	 */
	public Long hyperLogLogExamples(String hllKey) {
		lettuceStringRedisTemplate.opsForHyperLogLog().add(hllKey, "u1", "u2", "u3", "u1");
		Long size = lettuceStringRedisTemplate.opsForHyperLogLog().size(hllKey);
		log.info("HyperLogLog：key={} 估算去重数量={}", hllKey, size);
		return size;
	}

	/**
	 * GEO：地理位置与距离计算示例。
	 */
	public Distance geoExamples(String geoKey) {
		lettuceStringRedisTemplate.opsForGeo().add(geoKey, new Point(116.397128, 39.916527), "beijing");
		lettuceStringRedisTemplate.opsForGeo().add(geoKey, new Point(121.473701, 31.230416), "shanghai");
		Distance distance = lettuceStringRedisTemplate.opsForGeo().distance(geoKey, "beijing", "shanghai", Metrics.KILOMETERS);
		log.info("GEO：key={} 北京-上海 距离(千米)={}", geoKey, distance != null ? distance.getValue() : null);
		return distance;
	}

	/**
	 * Stream：简单的生产与范围读取示例。
	 */
	public List<MapRecord<String, Object, Object>> streamExamples(String streamKey) {
		lettuceStringRedisTemplate.opsForStream()
				.add(StreamRecords.mapBacked(Map.of("orderId", "1001", "amount", "88.8"))
						.withStreamKey(streamKey));
		log.info("Stream：key={} 追加一条订单消息；执行范围读取", streamKey);
		return lettuceStringRedisTemplate.opsForStream().range(streamKey, Range.unbounded());
	}
}


