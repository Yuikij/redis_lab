package com.soukon.config;

import org.redisson.api.RedissonClient;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisClientConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

@Configuration
public class RedisTemplatesConfig {

    private RedisStandaloneConfiguration standalone(RedisProperties props) {
        RedisStandaloneConfiguration conf = new RedisStandaloneConfiguration();
        conf.setHostName(props.getHost());
        conf.setPort(props.getPort());
        conf.setDatabase(props.getDatabase());
        if (props.getPassword() != null && !props.getPassword().isEmpty()) {
            conf.setPassword(props.getPassword());
        }
        return conf;
    }

    @Primary
    @Bean("lettuceConnectionFactory")
    public LettuceConnectionFactory lettuceConnectionFactory(RedisProperties props) {
        Duration timeout = props.getTimeout() != null ? props.getTimeout() : Duration.ofSeconds(2);
        LettuceClientConfiguration clientCfg = LettuceClientConfiguration.builder()
                .commandTimeout(timeout)
                .shutdownTimeout(Duration.ofSeconds(2))
                .build();
        return new LettuceConnectionFactory(standalone(props), clientCfg);
    }

    @Bean("jedisConnectionFactory")
    public JedisConnectionFactory jedisConnectionFactory(RedisProperties props) {
        Duration timeout = props.getTimeout() != null ? props.getTimeout() : Duration.ofSeconds(2);
        JedisClientConfiguration clientCfg = JedisClientConfiguration.builder()
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .build();
        return new JedisConnectionFactory(standalone(props), clientCfg);
    }

    @Bean("redissonConnectionFactory")
    public RedisConnectionFactory redissonConnectionFactory(RedissonClient redissonClient) {
        return new RedissonConnectionFactory(redissonClient);
    }

    @Bean("lettuceStringRedisTemplate")
    public StringRedisTemplate lettuceStringRedisTemplate(@Qualifier("lettuceConnectionFactory") RedisConnectionFactory cf) {
        return new StringRedisTemplate(cf);
    }

    @Bean("jedisStringRedisTemplate")
    public StringRedisTemplate jedisStringRedisTemplate(@Qualifier("jedisConnectionFactory") RedisConnectionFactory cf) {
        return new StringRedisTemplate(cf);
    }

    @Bean("redissonStringRedisTemplate")
    public StringRedisTemplate redissonStringRedisTemplate(@Qualifier("redissonConnectionFactory") RedisConnectionFactory cf) {
        return new StringRedisTemplate(cf);
    }
}


