package io.taskflow.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

/**
 * Spring Cache backed by Redis. Per-cache TTLs are configured explicitly so each
 * cache's stale-tolerance is reviewed individually instead of inheriting a global
 * default that nobody remembers.
 *
 * <p>Caches are key-prefixed with {@code taskflow:cache:<cache-name>:} so a shared
 * Redis instance (multiple environments/services) doesn't suffer key collisions.</p>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** Cache name for per-user membership list. Eviction is fine-grained per user. */
    public static final String USER_MEMBERSHIPS = "userMemberships";

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory cf,
                                          @Qualifier("redisObjectMapper") ObjectMapper mapper) {
        // Typed JSON for cache values — without this, List<MembershipView> round-trips as
        // List<LinkedHashMap> and login throws ClassCastException.
        ObjectMapper cacheMapper = mapper.copy();
        cacheMapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfBaseType(Object.class)
                        .build(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);
        GenericJackson2JsonRedisSerializer valueSer = new GenericJackson2JsonRedisSerializer(cacheMapper);

        RedisCacheConfiguration baseConfig = RedisCacheConfiguration.defaultCacheConfig()
                .computePrefixWith(name -> "taskflow:cache:" + name + ":")
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        valueSer));

        Map<String, RedisCacheConfiguration> perCache = Map.of(
                // Memberships rarely change; 5 min keeps role/membership changes
                // visible quickly without making the cache useless.
                USER_MEMBERSHIPS, baseConfig.entryTtl(Duration.ofMinutes(5))
        );

        return RedisCacheManager.builder(cf)
                .cacheDefaults(baseConfig.entryTtl(Duration.ofMinutes(1)))
                .withInitialCacheConfigurations(perCache)
                .transactionAware()  // align cache mutations with enclosing TX (afterCommit)
                .build();
    }
}
