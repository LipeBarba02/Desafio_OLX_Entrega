package dev.olx.moderation;

import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * Coordinates the external mock's quota across all worker replicas.
 * A JVM-local counter would let each pod independently spend the full 5 RPS quota.
 * Redis executes the token-bucket update atomically; failures are fail-closed.
 */
@Component
public class GlobalQuotaGate {
    private static final String KEY = "olx:moderation:global-token-bucket";

    /** Capacity 4; refill rate 4 tokens/second. Redis TIME avoids pod clock skew. */
    private static final String TOKEN_BUCKET_LUA = """
        local t = redis.call('TIME')
        local now = tonumber(t[1]) + tonumber(t[2]) / 1000000
        local state = redis.call('HMGET', KEYS[1], 'tokens', 'at')
        local tokens = tonumber(state[1]) or 4
        local previous = tonumber(state[2]) or now
        tokens = math.min(4, tokens + math.max(0, now - previous) * 4)
        local allowed = 0
        if tokens >= 1 then
            tokens = tokens - 1
            allowed = 1
        end
        redis.call('HSET', KEYS[1], 'tokens', tokens, 'at', now)
        redis.call('PEXPIRE', KEYS[1], 3000)
        return allowed
        """;

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> tokenBucketScript =
        new DefaultRedisScript<>(TOKEN_BUCKET_LUA, Long.class);

    public GlobalQuotaGate(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Waits for a token instead of calling the mock above the shared limit. */
    public void acquire() {
        while (true) {
            Long allowed = redis.execute(tokenBucketScript, List.of(KEY));
            if (allowed != null && allowed == 1L) {
                return;
            }

            try {
                Thread.sleep(75);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                    "Interrupted while waiting for moderation quota",
                    exception
                );
            }
        }
    }
}
