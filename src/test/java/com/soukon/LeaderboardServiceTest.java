package com.soukon;

import com.soukon.service.LeaderboardService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 排行榜服务测试
 */
@SpringBootTest
class LeaderboardServiceTest {

    @BeforeAll
    static void checkRedis(@Autowired org.springframework.core.env.Environment env) {
        RedisConnectivity.assumeRedis(env);
    }

    @Autowired
    private LeaderboardService leaderboardService;

    private String testLeaderboard;

    @BeforeEach
    void setUp() {
        // 为每个测试生成唯一的排行榜键，避免测试间相互影响
        testLeaderboard = "test:" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void testBasicScoreOperations() {
        // 测试添加分数
        Double score1 = leaderboardService.addScore(testLeaderboard, "user1", 100.0);
        assertEquals(100.0, score1, "初次添加分数应为100.0");

        // 测试增加分数
        Double score2 = leaderboardService.addScore(testLeaderboard, "user1", 50.0);
        assertEquals(150.0, score2, "累加后分数应为150.0");

        // 测试设置分数
        leaderboardService.setScore(testLeaderboard, "user1", 200.0);
        Double currentScore = leaderboardService.getUserScore(testLeaderboard, "user1");
        assertEquals(200.0, currentScore, "设置后分数应为200.0");
    }

    @Test
    void testRankingOperations() {
        // 准备测试数据
        leaderboardService.setScore(testLeaderboard, "alice", 1000.0);
        leaderboardService.setScore(testLeaderboard, "bob", 800.0);
        leaderboardService.setScore(testLeaderboard, "charlie", 1200.0);
        leaderboardService.setScore(testLeaderboard, "david", 900.0);

        // 测试获取用户排名
        Long aliceRank = leaderboardService.getUserRank(testLeaderboard, "alice");
        assertEquals(2L, aliceRank, "Alice 应排第2名");

        Long charlieRank = leaderboardService.getUserRank(testLeaderboard, "charlie");
        assertEquals(1L, charlieRank, "Charlie 应排第1名");

        Long bobRank = leaderboardService.getUserRank(testLeaderboard, "bob");
        assertEquals(4L, bobRank, "Bob 应排第4名");

        // 测试不存在用户的排名
        Long unknownRank = leaderboardService.getUserRank(testLeaderboard, "unknown");
        assertNull(unknownRank, "不存在的用户排名应为null");
    }

    @Test
    void testTopNLeaderboard() {
        // 准备测试数据
        Map<String, Double> scores = new HashMap<>();
        scores.put("player1", 1500.0);
        scores.put("player2", 1200.0);
        scores.put("player3", 1800.0);
        scores.put("player4", 1000.0);
        scores.put("player5", 1600.0);
        
        leaderboardService.batchAddScores(testLeaderboard, scores);

        // 测试TOP 3
        List<LeaderboardService.LeaderboardEntry> top3 = 
            leaderboardService.getTopN(testLeaderboard, 3);
        
        assertEquals(3, top3.size(), "应返回3个用户");
        assertEquals("player3", top3.get(0).getUserId(), "第1名应是player3");
        assertEquals(1800.0, top3.get(0).getScore(), "第1名分数应是1800.0");
        assertEquals(1, top3.get(0).getRank(), "第1名排名应是1");

        assertEquals("player5", top3.get(1).getUserId(), "第2名应是player5");
        assertEquals("player1", top3.get(2).getUserId(), "第3名应是player1");
    }

    @Test
    void testScoreRangeQuery() {
        // 准备测试数据
        leaderboardService.setScore(testLeaderboard, "low1", 100.0);
        leaderboardService.setScore(testLeaderboard, "mid1", 500.0);
        leaderboardService.setScore(testLeaderboard, "mid2", 600.0);
        leaderboardService.setScore(testLeaderboard, "high1", 1000.0);

        // 查询中等分数范围的用户
        List<LeaderboardService.LeaderboardEntry> midRange = 
            leaderboardService.getUsersByScoreRange(testLeaderboard, 400.0, 700.0);
        
        assertEquals(2, midRange.size(), "中等分数范围应有2个用户");
        assertTrue(midRange.stream().anyMatch(e -> e.getUserId().equals("mid1")), "应包含mid1");
        assertTrue(midRange.stream().anyMatch(e -> e.getUserId().equals("mid2")), "应包含mid2");
    }

    @Test
    void testUserNeighbors() {
        // 准备测试数据
        for (int i = 1; i <= 10; i++) {
            leaderboardService.setScore(testLeaderboard, "user" + i, i * 100.0);
        }

        // 测试用户5周围的用户（前后各2名）
        List<LeaderboardService.LeaderboardEntry> neighbors = 
            leaderboardService.getUserNeighbors(testLeaderboard, "user5", 2);
        
        // user5分数500，排名第6，周围应该是user3到user7
        assertEquals(5, neighbors.size(), "应返回5个用户（包括自己）");
        
        // 验证包含的用户
        assertTrue(neighbors.stream().anyMatch(e -> e.getUserId().equals("user3")), "应包含user3");
        assertTrue(neighbors.stream().anyMatch(e -> e.getUserId().equals("user5")), "应包含user5自己");
        assertTrue(neighbors.stream().anyMatch(e -> e.getUserId().equals("user7")), "应包含user7");
    }

    @Test
    void testUserManagement() {
        // 添加用户
        leaderboardService.setScore(testLeaderboard, "testUser", 500.0);
        assertEquals(1L, leaderboardService.getTotalUsers(testLeaderboard), "应有1个用户");

        // 删除用户
        Boolean removed = leaderboardService.removeUser(testLeaderboard, "testUser");
        assertTrue(removed, "删除应成功");
        assertEquals(0L, leaderboardService.getTotalUsers(testLeaderboard), "删除后应有0个用户");

        // 删除不存在的用户
        Boolean notRemoved = leaderboardService.removeUser(testLeaderboard, "nonexistent");
        assertFalse(notRemoved, "删除不存在用户应返回false");
    }

    @Test
    void testBatchOperations() {
        // 批量添加分数
        Map<String, Double> batchScores = new HashMap<>();
        batchScores.put("batch1", 100.0);
        batchScores.put("batch2", 200.0);
        batchScores.put("batch3", 300.0);
        
        leaderboardService.batchAddScores(testLeaderboard, batchScores);
        assertEquals(3L, leaderboardService.getTotalUsers(testLeaderboard), "批量添加后应有3个用户");

        // 验证批量添加的分数
        assertEquals(300.0, leaderboardService.getUserScore(testLeaderboard, "batch3"), "batch3分数应为300.0");
        assertEquals(1L, leaderboardService.getUserRank(testLeaderboard, "batch3"), "batch3应排第1名");
    }

    @Test
    void testDailyLeaderboard() {
        // 测试日榜功能
        Double dailyScore1 = leaderboardService.addDailyScore("game", "player1", 100.0);
        assertEquals(100.0, dailyScore1, "日榜初次添加分数应为100.0");

        Double dailyScore2 = leaderboardService.addDailyScore("game", "player1", 50.0);
        assertEquals(150.0, dailyScore2, "日榜累加后分数应为150.0");

        leaderboardService.addDailyScore("game", "player2", 120.0);

        // 获取今日TOP 2
        List<LeaderboardService.LeaderboardEntry> todayTop = 
            leaderboardService.getTodayTopN("game", 2);
        
        assertEquals(2, todayTop.size(), "今日TOP应有2个用户");
        assertEquals("player1", todayTop.get(0).getUserId(), "今日第1名应是player1");
        assertEquals(150.0, todayTop.get(0).getScore(), "今日第1名分数应是150.0");
    }

    @Test
    void testWeeklyAndMonthlyLeaderboard() {
        // 测试周榜
        leaderboardService.addWeeklyScore("game", "weekPlayer1", 500.0);
        leaderboardService.addWeeklyScore("game", "weekPlayer2", 300.0);
        
        List<LeaderboardService.LeaderboardEntry> weekTop = 
            leaderboardService.getThisWeekTopN("game", 2);
        assertEquals(2, weekTop.size(), "本周TOP应有2个用户");

        // 测试月榜
        leaderboardService.addMonthlyScore("game", "monthPlayer1", 1000.0);
        leaderboardService.addMonthlyScore("game", "monthPlayer2", 800.0);
        
        List<LeaderboardService.LeaderboardEntry> monthTop = 
            leaderboardService.getThisMonthTopN("game", 2);
        assertEquals(2, monthTop.size(), "本月TOP应有2个用户");
        assertEquals("monthPlayer1", monthTop.get(0).getUserId(), "本月第1名应是monthPlayer1");
    }

    @Test
    void testClearLeaderboard() {
        // 添加一些数据
        leaderboardService.setScore(testLeaderboard, "user1", 100.0);
        leaderboardService.setScore(testLeaderboard, "user2", 200.0);
        assertEquals(2L, leaderboardService.getTotalUsers(testLeaderboard), "清空前应有2个用户");

        // 清空排行榜
        leaderboardService.clearLeaderboard(testLeaderboard);
        assertEquals(0L, leaderboardService.getTotalUsers(testLeaderboard), "清空后应有0个用户");
    }

    @Test
    void testLeaderboardEntryEquality() {
        // 测试排行榜条目的相等性
        LeaderboardService.LeaderboardEntry entry1 = 
            new LeaderboardService.LeaderboardEntry(1, "player1", 100.0);
        LeaderboardService.LeaderboardEntry entry2 = 
            new LeaderboardService.LeaderboardEntry(1, "player1", 100.0);
        LeaderboardService.LeaderboardEntry entry3 = 
            new LeaderboardService.LeaderboardEntry(2, "player1", 100.0);

        assertEquals(entry1, entry2, "相同内容的条目应相等");
        assertNotEquals(entry1, entry3, "不同排名的条目应不相等");
        assertEquals(entry1.hashCode(), entry2.hashCode(), "相同条目的hashCode应相等");
        
        // 测试toString
        String expected = "第1名: player1 (100.0分)";
        assertEquals(expected, entry1.toString(), "toString格式应正确");
    }
}
