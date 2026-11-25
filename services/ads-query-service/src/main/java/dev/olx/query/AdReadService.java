package dev.olx.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/** Read use case: OpenSearch for search and Redis cache-aside for approved details. */
@Service
public class AdReadService {
    private static final Duration FILL_LOCK_TTL = Duration.ofSeconds(5);
    private static final int FALLBACK_CONCURRENCY = 32;
    private static final DefaultRedisScript<Long> RELEASE_LOCK = new DefaultRedisScript<>(
        "if redis.call('GET',KEYS[1])==ARGV[1] then return redis.call('DEL',KEYS[1]) else return 0 end",
        Long.class
    );

    private final OpenSearchClient openSearch;
    private final AdsCommandClient adsCommand;
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final MeterRegistry metrics;
    private final Semaphore fallbackBulkhead = new Semaphore(FALLBACK_CONCURRENCY);

    public AdReadService(
        OpenSearchClient openSearch,
        AdsCommandClient adsCommand,
        StringRedisTemplate redis,
        ObjectMapper json,
        MeterRegistry metrics
    ) {
        this.openSearch = openSearch;
        this.adsCommand = adsCommand;
        this.redis = redis;
        this.json = json;
        this.metrics = metrics;
    }

    public SearchPage search(String text, String category, int size, String cursor) {
        return openSearch.search(text, category, size, cursor);
    }

    /** Cache hit avoids both OpenSearch and the internal HTTP detail request. */
    public AdDetailResponse detail(UUID adId) {
        String cacheKey = AdProjectionConsumer.detailKey(adId);
        String lockKey = cacheKey + ":lock";
        String lockToken = UUID.randomUUID().toString();

        try {
            AdDetailResponse cached = readCache(cacheKey);
            if (cached != null) {
                metrics.counter("ads.cache.hit").increment();
                return cached;
            }
            metrics.counter("ads.cache.miss").increment();

            Boolean ownsFill = redis.opsForValue().setIfAbsent(lockKey, lockToken, FILL_LOCK_TTL);
            if (Boolean.TRUE.equals(ownsFill)) {
                try {
                    // The first cache read and SETNX are separate commands; another owner may have filled in between.
                    AdDetailResponse filledByOther = readCache(cacheKey);
                    if (filledByOther != null) {
                        return filledByOther;
                    }

                    AdDetailResponse fromSources = approvedDetail(adId);
                    if (fromSources == null) {
                        return null;
                    }

                    // Jitter prevents many popular keys from expiring at the same instant.
                    Duration ttl = Duration.ofSeconds(240 + ThreadLocalRandom.current().nextInt(120));
                    redis.opsForValue().set(cacheKey, json.writeValueAsString(fromSources), ttl);
                    return fromSources;
                } finally {
                    // Compare token before deleting; never release a lock acquired by a newer request.
                    redis.execute(RELEASE_LOCK, List.of(lockKey), lockToken);
                }
            }

            // Coalesce concurrent cache misses for this key; after 500 ms use protected fallback.
            for (int attempt = 0; attempt < 10; attempt++) {
                Thread.sleep(50);
                AdDetailResponse filled = readCache(cacheKey);
                if (filled != null) {
                    metrics.counter("ads.cache.coalesced").increment();
                    return filled;
                }
            }
            return fallback(adId);
        } catch (DataAccessException redisUnavailable) {
            metrics.counter("ads.cache.fallback").increment();
            return fallback(adId);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return fallback(adId);
        } catch (Exception serializationFailure) {
            throw new IllegalStateException("Could not read or write cached ad detail", serializationFailure);
        }
    }

    private AdDetailResponse readCache(String key) throws Exception {
        String jsonValue = redis.opsForValue().get(key);
        return jsonValue == null ? null : json.readValue(jsonValue, AdDetailResponse.class);
    }

    /** Fallback bulkhead prevents a cache outage from sending unbounded traffic to source dependencies. */
    private AdDetailResponse fallback(UUID adId) {
        if (!fallbackBulkhead.tryAcquire()) {
            metrics.counter("ads.cache.fallback.rejected").increment();
            throw new QueryCapacityExceededException();
        }
        try {
            return approvedDetail(adId);
        } finally {
            fallbackBulkhead.release();
        }
    }

    /** OpenSearch confirms approval; Ads Command supplies canonical fields and image URLs. */
    private AdDetailResponse approvedDetail(UUID adId) {
        SearchDocument indexed = openSearch.find(adId);
        if (indexed == null) {
            return null;
        }

        AdDetailResponse source = adsCommand.getDetail(adId);
        if (source == null) {
            return null;
        }

        // The query projection only contains approved ids; command status may lag its decision event briefly.
        return new AdDetailResponse(
            source.id(),
            source.title(),
            source.description(),
            source.price(),
            source.category(),
            source.images(),
            "APPROVED",
            null,
            source.createdAt()
        );
    }
}
