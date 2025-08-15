package com.soukon.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Redis 排行榜服务
 * 使用 ZSet 实现高性能排行榜功能，支持：
 * - 实时更新分数
 * - 快速获取排名
 * - TOP N 查询
 * - 分数范围查询
 * - 周期性排行榜（日榜、周榜、月榜）
 */
@Service
public class LeaderboardService {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardService.class);
    
    private final StringRedisTemplate redisTemplate;
    
    // 排行榜键前缀
    private static final String LEADERBOARD_PREFIX = "leaderboard:";
    private static final String DAILY_PREFIX = "daily:";
    private static final String WEEKLY_PREFIX = "weekly:";
    private static final String MONTHLY_PREFIX = "monthly:";
    
    public LeaderboardService(@Qualifier("lettuceStringRedisTemplate") StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 添加或更新用户分数
     * @param leaderboardKey 排行榜键
     * @param userId 用户ID
     * @param score 分数
     * @return 操作后的总分数
     */
    public Double addScore(String leaderboardKey, String userId, double score) {
        String key = LEADERBOARD_PREFIX + leaderboardKey;
        Double newScore = redisTemplate.opsForZSet().incrementScore(key, userId, score);
        log.info("用户 {} 在排行榜 {} 中增加 {} 分，当前总分: {}", userId, leaderboardKey, score, newScore);
        return newScore;
    }

    /**
     * 设置用户分数（覆盖原有分数）
     * @param leaderboardKey 排行榜键
     * @param userId 用户ID
     * @param score 分数
     */
    public void setScore(String leaderboardKey, String userId, double score) {
        String key = LEADERBOARD_PREFIX + leaderboardKey;
        redisTemplate.opsForZSet().add(key, userId, score);
        log.info("设置用户 {} 在排行榜 {} 中的分数为: {}", userId, leaderboardKey, score);
    }

    /**
     * 获取用户当前分数
     * @param leaderboardKey 排行榜键
     * @param userId 用户ID
     * @return 用户分数，如果用户不存在则返回null
     */
    public Double getUserScore(String leaderboardKey, String userId) {
        String key = LEADERBOARD_PREFIX + leaderboardKey;
        return redisTemplate.opsForZSet().score(key, userId);
    }

    /**
     * 获取用户排名（从1开始）
     * @param leaderboardKey 排行榜键
     * @param userId 用户ID
     * @return 用户排名，如果用户不存在则返回null
     */
    public Long getUserRank(String leaderboardKey, String userId) {
        String key = LEADERBOARD_PREFIX + leaderboardKey;
        Long rank = redisTemplate.opsForZSet().reverseRank(key, userId);
        return rank != null ? rank + 1 : null; // Redis rank 从0开始，转换为从1开始
    }

    /**
     * 获取TOP N 排行榜
     * @param leaderboardKey 排行榜键
     * @param topN 获取前N名
     * @return 排行榜列表，包含用户ID、分数和排名
     */
    public List<LeaderboardEntry> getTopN(String leaderboardKey, int topN) {
        String key = LEADERBOARD_PREFIX + leaderboardKey;
        Set<ZSetOperations.TypedTuple<String>> topUsers = 
            redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, topN - 1);
        
        List<LeaderboardEntry> result = new ArrayList<>();
        int rank = 1;
        if (topUsers != null) {
            for (ZSetOperations.TypedTuple<String> user : topUsers) {
                result.add(new LeaderboardEntry(rank++, user.getValue(), user.getScore()));
            }
        }
        
        log.info("获取排行榜 {} 的 TOP {}: {} 个用户", leaderboardKey, topN, result.size());
        return result;
    }

    /**
     * 获取指定分数范围内的用户
     * @param leaderboardKey 排行榜键
     * @param minScore 最小分数
     * @param maxScore 最大分数
     * @return 分数范围内的用户列表
     */
    public List<LeaderboardEntry> getUsersByScoreRange(String leaderboardKey, double minScore, double maxScore) {
        String key = LEADERBOARD_PREFIX + leaderboardKey;
        Set<ZSetOperations.TypedTuple<String>> users = 
            redisTemplate.opsForZSet().reverseRangeByScoreWithScores(key, minScore, maxScore);
        
        List<LeaderboardEntry> result = new ArrayList<>();
        if (users != null) {
            for (ZSetOperations.TypedTuple<String> user : users) {
                Long rank = getUserRank(leaderboardKey, user.getValue());
                result.add(new LeaderboardEntry(rank != null ? rank.intValue() : 0, 
                                              user.getValue(), user.getScore()));
            }
        }
        
        log.info("获取排行榜 {} 分数范围 [{}, {}] 内的用户: {} 个", leaderboardKey, minScore, maxScore, result.size());
        return result;
    }

    /**
     * 获取用户周围的排名情况
     * @param leaderboardKey 排行榜键
     * @param userId 用户ID
     * @param range 获取用户前后各range名的用户
     * @return 用户周围的排行榜
     */
    public List<LeaderboardEntry> getUserNeighbors(String leaderboardKey, String userId, int range) {
        Long userRank = getUserRank(leaderboardKey, userId);
        if (userRank == null) {
            return Collections.emptyList();
        }
        
        long start = Math.max(0, userRank - range - 1); // Redis rank从0开始
        long end = userRank + range - 1;
        
        String key = LEADERBOARD_PREFIX + leaderboardKey;
        Set<ZSetOperations.TypedTuple<String>> neighbors = 
            redisTemplate.opsForZSet().reverseRangeWithScores(key, start, end);
        
        List<LeaderboardEntry> result = new ArrayList<>();
        int rank = (int) start + 1;
        if (neighbors != null) {
            for (ZSetOperations.TypedTuple<String> user : neighbors) {
                result.add(new LeaderboardEntry(rank++, user.getValue(), user.getScore()));
            }
        }
        
        log.info("获取用户 {} 周围 ±{} 名的排行榜: {} 个用户", userId, range, result.size());
        return result;
    }

    /**
     * 获取排行榜总人数
     * @param leaderboardKey 排行榜键
     * @return 总人数
     */
    public Long getTotalUsers(String leaderboardKey) {
        String key = LEADERBOARD_PREFIX + leaderboardKey;
        return redisTemplate.opsForZSet().zCard(key);
    }

    /**
     * 删除用户
     * @param leaderboardKey 排行榜键
     * @param userId 用户ID
     * @return 是否删除成功
     */
    public Boolean removeUser(String leaderboardKey, String userId) {
        String key = LEADERBOARD_PREFIX + leaderboardKey;
        Long removed = redisTemplate.opsForZSet().remove(key, userId);
        log.info("从排行榜 {} 中删除用户 {}: {}", leaderboardKey, userId, removed > 0 ? "成功" : "失败");
        return removed > 0;
    }

    /**
     * 清空排行榜
     * @param leaderboardKey 排行榜键
     */
    public void clearLeaderboard(String leaderboardKey) {
        String key = LEADERBOARD_PREFIX + leaderboardKey;
        redisTemplate.delete(key);
        log.info("清空排行榜: {}", leaderboardKey);
    }

    // ================== 周期性排行榜功能 ==================

    /**
     * 添加日榜分数
     */
    public Double addDailyScore(String leaderboardKey, String userId, double score) {
        String dateKey = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        return addScore(DAILY_PREFIX + dateKey + ":" + leaderboardKey, userId, score);
    }

    /**
     * 获取今日TOP N
     */
    public List<LeaderboardEntry> getTodayTopN(String leaderboardKey, int topN) {
        String dateKey = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        return getTopN(DAILY_PREFIX + dateKey + ":" + leaderboardKey, topN);
    }

    /**
     * 添加周榜分数
     */
    public Double addWeeklyScore(String leaderboardKey, String userId, double score) {
        // 使用年份和周数作为键
        LocalDate now = LocalDate.now();
        String weekKey = now.getYear() + "-W" + String.format("%02d", getWeekOfYear(now));
        return addScore(WEEKLY_PREFIX + weekKey + ":" + leaderboardKey, userId, score);
    }

    /**
     * 获取本周TOP N
     */
    public List<LeaderboardEntry> getThisWeekTopN(String leaderboardKey, int topN) {
        LocalDate now = LocalDate.now();
        String weekKey = now.getYear() + "-W" + String.format("%02d", getWeekOfYear(now));
        return getTopN(WEEKLY_PREFIX + weekKey + ":" + leaderboardKey, topN);
    }

    /**
     * 添加月榜分数
     */
    public Double addMonthlyScore(String leaderboardKey, String userId, double score) {
        String monthKey = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        return addScore(MONTHLY_PREFIX + monthKey + ":" + leaderboardKey, userId, score);
    }

    /**
     * 获取本月TOP N
     */
    public List<LeaderboardEntry> getThisMonthTopN(String leaderboardKey, int topN) {
        String monthKey = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        return getTopN(MONTHLY_PREFIX + monthKey + ":" + leaderboardKey, topN);
    }

    /**
     * 批量添加分数（适用于批量导入或数据迁移）
     * @param leaderboardKey 排行榜键
     * @param scores 用户分数映射
     */
    public void batchAddScores(String leaderboardKey, Map<String, Double> scores) {
        String key = LEADERBOARD_PREFIX + leaderboardKey;
        Set<ZSetOperations.TypedTuple<String>> tuples = scores.entrySet().stream()
            .map(entry -> ZSetOperations.TypedTuple.of(entry.getKey(), entry.getValue()))
            .collect(Collectors.toSet());
        
        Long added = redisTemplate.opsForZSet().add(key, tuples);
        log.info("批量添加分数到排行榜 {}: {} 个用户，实际添加: {}", leaderboardKey, scores.size(), added);
    }

    // 辅助方法：获取一年中的第几周
    private int getWeekOfYear(LocalDate date) {
        return date.getDayOfYear() / 7 + 1;
    }

    /**
     * 排行榜条目
     */
    public static class LeaderboardEntry {
        private final int rank;
        private final String userId;
        private final Double score;

        public LeaderboardEntry(int rank, String userId, Double score) {
            this.rank = rank;
            this.userId = userId;
            this.score = score;
        }

        public int getRank() { return rank; }
        public String getUserId() { return userId; }
        public Double getScore() { return score; }

        @Override
        public String toString() {
            return String.format("第%d名: %s (%.1f分)", rank, userId, score);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            LeaderboardEntry that = (LeaderboardEntry) o;
            return rank == that.rank && 
                   Objects.equals(userId, that.userId) && 
                   Objects.equals(score, that.score);
        }

        @Override
        public int hashCode() {
            return Objects.hash(rank, userId, score);
        }
    }
}
