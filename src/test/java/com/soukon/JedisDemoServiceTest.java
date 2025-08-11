package com.soukon;

import com.soukon.service.JedisDemoService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class JedisDemoServiceTest {

    @BeforeAll
    static void checkRedis(@org.springframework.beans.factory.annotation.Autowired org.springframework.core.env.Environment env) {
        RedisConnectivity.assumeRedis(env);
    }

    @Autowired
    private JedisDemoService jedisDemoService;

    @Test
    void testSetGet() {
        String res = jedisDemoService.setAndGet("jedis:test:k", "v");
        assertEquals("v", res);
    }

    @Test
    void testPipeline() {
        List<Object> out = jedisDemoService.pipelineDemo();
        assertTrue(out.size() >= 3);
    }

    @Test
    void testTransaction() {
        List<Object> out = jedisDemoService.transactionDemo("sku:test");
        assertNotNull(out);
    }
}


