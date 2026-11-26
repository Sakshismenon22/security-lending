package com.example.market_data_service.config;

import com.example.market_data_service.dto.SecurityCandleResponse;
import com.example.market_data_service.model.SecurityMaster;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableCaching
@Profile("redis")
public class MarketDataCacheConfig implements CachingConfigurer {

    public static final String HISTORY = "candle-history";
    public static final String SECURITIES = "securities";
    public static final String ALL_SECURITIES = "all";

    @Bean
    RedisCacheManager marketDataCacheManager(
            RedisConnectionFactory redis, @Value("${market-data.cache.ttl:45s}") Duration ttl) {
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("market-data.cache.ttl must be positive");
        }
        var defaults = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .prefixCacheNameWith("security-lending:market-data:v1:")
                .entryTtl(ttl);
        return RedisCacheManager.builder(redis)
                .cacheDefaults(defaults)
                .withCacheConfiguration(HISTORY, withListSerializer(defaults, SecurityCandleResponse.class))
                .withCacheConfiguration(SECURITIES, withListSerializer(defaults, SecurityMaster.class))
                .disableCreateOnMissingCache()
                .build();
    }

    private static RedisCacheConfiguration withListSerializer(
            RedisCacheConfiguration config, Class<?> elementType) {
        var mapper = JsonMapper.builder().build();
        var type = mapper.getTypeFactory().constructCollectionType(List.class, elementType);
        return config.serializeValuesWith(
                SerializationPair.fromSerializer(new JacksonJsonRedisSerializer<>(mapper, type)));
    }

    @Bean
    CacheInvalidationListener marketDataCacheInvalidation(RedisCacheManager manager) {
        return new CacheInvalidationListener(manager);
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException error, Cache cache, Object key) {
                warn("read", cache.getName(), error);
            }

            @Override
            public void handleCachePutError(RuntimeException error, Cache cache, Object key, Object value) {
                warn("write", cache.getName(), error);
            }

            @Override
            public void handleCacheEvictError(RuntimeException error, Cache cache, Object key) {
                warn("eviction", cache.getName(), error);
            }

            @Override
            public void handleCacheClearError(RuntimeException error, Cache cache) {
                warn("clear", cache.getName(), error);
            }
        };
    }

    private static void warn(String operation, String cache, RuntimeException error) {
        log.warn("Redis cache {} failed for {} ({}); PostgreSQL remains the source of truth",
                operation, cache, error.getClass().getSimpleName());
    }

    public record Invalidate(String cache, String key) {}

    static class CacheInvalidationListener {
        private final RedisCacheManager manager;

        CacheInvalidationListener(RedisCacheManager manager) {
            this.manager = manager;
        }

        @TransactionalEventListener(fallbackExecution = true)
        public void invalidate(Invalidate event) {
            try {
                manager.getCache(event.cache()).evict(event.key());
            } catch (RuntimeException error) {
                warn("eviction", event.cache(), error);
            }
        }
    }
}
