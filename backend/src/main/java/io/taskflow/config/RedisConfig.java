package io.taskflow.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.taskflow.dto.event.BoardEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis wiring.
 *
 * <p>Two templates are exposed:</p>
 * <ul>
 *   <li>{@link StringRedisTemplate} — for rate-limiter counters and any future raw-string
 *       ops. String values are atomically incrementable; objects are not.</li>
 *   <li>{@code RedisTemplate<String, BoardEvent>} — for pub/sub serialization. Bound to
 *       a Jackson serializer with {@code JavaTimeModule} so {@code Instant} fields
 *       round-trip as ISO-8601 strings (queryable from redis-cli, debuggable).</li>
 * </ul>
 *
 * <p>The Lua rate-limit script is loaded once and cached client-side; Spring Data
 * Redis automatically falls back to EVAL when EVALSHA misses on a fresh server.</p>
 */
@Configuration
public class RedisConfig {

    /** Single ObjectMapper used by Redis serializers — separate from MVC's mapper. */
    @Bean(name = "redisObjectMapper")
    public ObjectMapper redisObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    /**
     * Template for BoardEvent fan-out. Jackson-serialized so events are inspectable
     * with {@code redis-cli MONITOR} — non-trivial in production debugging.
     */
    @Bean
    public RedisTemplate<String, BoardEvent> boardEventRedisTemplate(
            RedisConnectionFactory cf,
            @org.springframework.beans.factory.annotation.Qualifier("redisObjectMapper")
            ObjectMapper mapper) {
        RedisTemplate<String, BoardEvent> tpl = new RedisTemplate<>();
        tpl.setConnectionFactory(cf);
        StringRedisSerializer keys = new StringRedisSerializer();
        Jackson2JsonRedisSerializer<BoardEvent> values =
                new Jackson2JsonRedisSerializer<>(mapper, BoardEvent.class);
        tpl.setKeySerializer(keys);
        tpl.setHashKeySerializer(keys);
        tpl.setValueSerializer(values);
        tpl.setHashValueSerializer(values);
        tpl.afterPropertiesSet();
        return tpl;
    }

    /**
     * Single shared listener container — handles all subscriptions (extensible to
     * additional channels without spawning multiple connection pools).
     */
    @Bean(destroyMethod = "destroy")
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory cf) {
        RedisMessageListenerContainer c = new RedisMessageListenerContainer();
        c.setConnectionFactory(cf);
        return c;
    }

    /** Lua: atomic INCR + EXPIRE on first hit. Returns the post-increment counter. */
    @Bean
    public RedisScript<Long> rateLimitScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(
                "local current = redis.call('INCR', KEYS[1]) " +
                "if current == 1 then " +
                "  redis.call('PEXPIRE', KEYS[1], ARGV[1]) " +
                "end " +
                "return current");
        script.setResultType(Long.class);
        return script;
    }
}
