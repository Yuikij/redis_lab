package com.soukon;

import com.soukon.service.LettuceDemoService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class LettuceDemoServiceTest {

    @BeforeAll
    static void checkRedis(@org.springframework.beans.factory.annotation.Autowired org.springframework.core.env.Environment env) {
        RedisConnectivity.assumeRedis(env);
    }

    @Autowired
    private LettuceDemoService lettuceDemoService;

    @Test
    void testSync() {
        String res = lettuceDemoService.syncSetGet("lettuce:test:k", "v");
        assertEquals("v", res);
    }

    @Test
    void testAsyncReactive() {
        String asyncGet = lettuceDemoService.asyncGet("lettuce:test:k").block();
        assertEquals("v", asyncGet);

        String reactiveGet = lettuceDemoService.reactiveSetGet("lettuce:test:r", "rv").block();
        assertEquals("rv", reactiveGet);
    }
}


