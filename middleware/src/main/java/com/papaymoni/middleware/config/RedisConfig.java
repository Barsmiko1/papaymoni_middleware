
package com.papaymoni.middleware.config;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.cache.CacheManager;
//import org.springframework.cache.annotation.EnableCaching;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.data.redis.cache.RedisCacheConfiguration;
//import org.springframework.data.redis.cache.RedisCacheManager;
//import org.springframework.data.redis.connection.RedisConnectionFactory;
//import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
//import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
//import org.springframework.data.redis.core.RedisTemplate;
//import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
//import org.springframework.data.redis.serializer.RedisSerializationContext;
//import org.springframework.data.redis.serializer.StringRedisSerializer;
//
//import java.time.Duration;
//import java.util.HashMap;
//import java.util.Map;
//

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

///**
// * Redis configuration for caching
// * Implements Java 8 date/time support and custom cache TTLs
// */
//@Configuration
//@EnableCaching
//public class RedisConfig {
//
//    @Value("${spring.redis.host}")
//    private String redisHost;
//
//    @Value("${spring.redis.port}")
//    private int redisPort;
//
//    @Value("${spring.redis.password:#{null}}")
//    private String redisPassword;
//
//    @Bean
//    public LettuceConnectionFactory redisConnectionFactory() {
//        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(redisHost, redisPort);
//        if (redisPassword != null) {
//            configuration.setPassword(redisPassword);
//        }
//        return new LettuceConnectionFactory(configuration);
//    }
//
//    @Bean
//    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
//        RedisTemplate<String, Object> template = new RedisTemplate<>();
//        template.setConnectionFactory(connectionFactory);
//
//        // Configure serializers with Java 8 date/time support
//        ObjectMapper objectMapper = new ObjectMapper();
//        objectMapper.registerModule(new JavaTimeModule());
//        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);
//
//        template.setKeySerializer(new StringRedisSerializer());
//        template.setValueSerializer(jsonSerializer);
//        template.setHashKeySerializer(new StringRedisSerializer());
//        template.setHashValueSerializer(jsonSerializer);
//
//        return template;
//    }
//
//    @Bean
//    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
//        // Configure serializers with Java 8 date/time support
//        ObjectMapper objectMapper = new ObjectMapper();
//        objectMapper.registerModule(new JavaTimeModule());
//        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);
//
//        // Default cache configuration
//        RedisCacheConfiguration defaultCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
//                .entryTtl(Duration.ofMinutes(10))
//                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
//                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer))
//                .disableCachingNullValues();
//
//        // Configure specific cache TTLs
//        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
//
//        // Set shorter TTL for order processing caches to ensure frequent refreshes
//        cacheConfigurations.put("pendingBuyOrders", defaultCacheConfig.entryTtl(Duration.ofSeconds(15)));
//        cacheConfigurations.put("pendingSellOrders", defaultCacheConfig.entryTtl(Duration.ofSeconds(15)));
//
//        // Set longer TTL for less frequently changing data
//        cacheConfigurations.put("userCredentials", defaultCacheConfig.entryTtl(Duration.ofHours(1)));
//        cacheConfigurations.put("orderDetails", defaultCacheConfig.entryTtl(Duration.ofMinutes(30)));
//        cacheConfigurations.put("transactionHistory", defaultCacheConfig.entryTtl(Duration.ofMinutes(15)));
//        cacheConfigurations.put("users", defaultCacheConfig.entryTtl(Duration.ofMinutes(5)));
//
//        // New cache configuration for user profiles with virtual accounts
//        cacheConfigurations.put("userProfiles", defaultCacheConfig.entryTtl(Duration.ofMinutes(10)));
//
//        return RedisCacheManager.builder(connectionFactory)
//                .cacheDefaults(defaultCacheConfig)
//                .withInitialCacheConfigurations(cacheConfigurations)
//                .transactionAware()
//                .build();
//    }
//}


@Configuration
@EnableCaching
public class RedisConfig {

    @Value("${spring.redis.host}")
    private String redisHost;

    @Value("${spring.redis.port}")
    private int redisPort;

    @Value("${spring.redis.password:#{null}}")
    private String redisPassword;

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(redisHost, redisPort);
        if (redisPassword != null) {
            configuration.setPassword(redisPassword);
        }

        // Configure connection pooling
        LettucePoolingClientConfiguration clientConfig = LettucePoolingClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(5))
                .shutdownTimeout(Duration.ZERO)
                .poolConfig(poolConfig())
                .build();

        return new LettuceConnectionFactory(configuration, clientConfig);
    }

    @Bean
    public GenericObjectPoolConfig poolConfig() {
        GenericObjectPoolConfig config = new GenericObjectPoolConfig();
        config.setMaxTotal(100);
        config.setMaxIdle(50);
        config.setMinIdle(20);
        config.setTestOnBorrow(true);
        config.setTestOnReturn(true);
        config.setTestWhileIdle(true);
        config.setMinEvictableIdleTimeMillis(Duration.ofSeconds(60).toMillis());
        config.setTimeBetweenEvictionRunsMillis(Duration.ofSeconds(30).toMillis());
        config.setNumTestsPerEvictionRun(3);
        config.setBlockWhenExhausted(true);
        return config;
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Configure serializers with Java 8 date/time support
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // Configure JSON serializer for values
        Jackson2JsonRedisSerializer<Object> jsonSerializer =
                new Jackson2JsonRedisSerializer<>(Object.class);
        jsonSerializer.setObjectMapper(objectMapper);

        // Configure string serializer for keys (faster lookups)
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.setEnableTransactionSupport(true);

        return template;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Configure serializers with Java 8 date/time support
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        Jackson2JsonRedisSerializer<Object> jsonSerializer =
                new Jackson2JsonRedisSerializer<>(Object.class);
        jsonSerializer.setObjectMapper(objectMapper);

        // Default cache configuration
        RedisCacheConfiguration defaultCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer))
                .disableCachingNullValues()
                .prefixCacheNameWith("papaymoni:"); // Add namespace to prevent conflicts

        // Configure specific cache TTLs
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // Frequently updated caches
        cacheConfigurations.put("pendingBuyOrders", defaultCacheConfig.entryTtl(Duration.ofSeconds(15)));
        cacheConfigurations.put("pendingSellOrders", defaultCacheConfig.entryTtl(Duration.ofSeconds(15)));

        // User-related caches
        cacheConfigurations.put("users", defaultCacheConfig.entryTtl(Duration.ofMinutes(15)));
        cacheConfigurations.put("userProfiles", defaultCacheConfig.entryTtl(Duration.ofMinutes(10)));
        cacheConfigurations.put("userAuth", defaultCacheConfig.entryTtl(Duration.ofHours(24)));
        cacheConfigurations.put("userVirtualAccounts", defaultCacheConfig.entryTtl(Duration.ofMinutes(5)));

        // BVN verification results
        cacheConfigurations.put("bvnVerification", defaultCacheConfig.entryTtl(Duration.ofDays(7)));

        // Order-related caches
        cacheConfigurations.put("orderDetails", defaultCacheConfig.entryTtl(Duration.ofMinutes(5)));
        cacheConfigurations.put("transactionHistory", defaultCacheConfig.entryTtl(Duration.ofMinutes(15)));

        // Bybit API response caches
        cacheConfigurations.put("bybitApiResponses", defaultCacheConfig.entryTtl(Duration.ofSeconds(30)));
        cacheConfigurations.put("credentialsVerification", defaultCacheConfig.entryTtl(Duration.ofHours(1)));
        cacheConfigurations.put("personalAds", defaultCacheConfig.entryTtl(Duration.ofMinutes(5)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultCacheConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();
    }
}