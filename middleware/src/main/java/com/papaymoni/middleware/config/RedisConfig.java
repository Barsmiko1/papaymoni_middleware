//package com.papaymoni.middleware.config;
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
//        return RedisCacheManager.builder(connectionFactory)
//                .cacheDefaults(defaultCacheConfig)
//                .withInitialCacheConfigurations(cacheConfigurations)
//                .transactionAware()
//                .build();
//    }
//}



package com.papaymoni.middleware.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis configuration for caching
 * Implements Java 8 date/time support and custom cache TTLs
 */
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
        return new LettuceConnectionFactory(configuration);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Configure serializers with Java 8 date/time support
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(jsonSerializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(jsonSerializer);

        return template;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Configure serializers with Java 8 date/time support
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        // Default cache configuration
        RedisCacheConfiguration defaultCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer))
                .disableCachingNullValues();

        // Configure specific cache TTLs
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // Set shorter TTL for order processing caches to ensure frequent refreshes
        cacheConfigurations.put("pendingBuyOrders", defaultCacheConfig.entryTtl(Duration.ofSeconds(15)));
        cacheConfigurations.put("pendingSellOrders", defaultCacheConfig.entryTtl(Duration.ofSeconds(15)));

        // Set longer TTL for less frequently changing data
        cacheConfigurations.put("userCredentials", defaultCacheConfig.entryTtl(Duration.ofHours(1)));
        cacheConfigurations.put("orderDetails", defaultCacheConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigurations.put("transactionHistory", defaultCacheConfig.entryTtl(Duration.ofMinutes(15)));
        cacheConfigurations.put("users", defaultCacheConfig.entryTtl(Duration.ofMinutes(5)));

        // New cache configuration for user profiles with virtual accounts
        cacheConfigurations.put("userProfiles", defaultCacheConfig.entryTtl(Duration.ofMinutes(10)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultCacheConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();
    }
}