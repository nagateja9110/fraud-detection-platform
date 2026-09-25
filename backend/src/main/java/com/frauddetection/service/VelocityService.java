package com.frauddetection.service;

import com.frauddetection.dto.VelocitySnapshotDto;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis-backed transaction velocity tracking.
 *
 * Per account, a sorted set holds one entry per recent transaction
 * (member = transactionId, score = epoch millis) so counts/pruning are O(log N)
 * range operations. A parallel hash holds transactionId -> amount so window
 * sums can be computed without a second round trip per transaction. Both keys
 * expire on their own after the longest window (24h) of inactivity, so idle
 * accounts don't leak memory.
 */
@Service
public class VelocityService {

    private static final Duration WINDOW_1H = Duration.ofHours(1);
    private static final Duration WINDOW_24H = Duration.ofHours(24);
    private static final Duration KEY_TTL = Duration.ofHours(25);

    private final StringRedisTemplate redis;

    public VelocityService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    private String tsKey(String accountNumber) {
        return "velocity:%s:ts".formatted(accountNumber);
    }

    private String amtKey(String accountNumber) {
        return "velocity:%s:amt".formatted(accountNumber);
    }

    /**
     * Records this transaction and returns the account's velocity snapshot
     * (including this transaction) over the 1h/24h windows.
     */
    public VelocitySnapshotDto recordAndSnapshot(String accountNumber, String transactionId, BigDecimal amount, Instant now) {
        String tsKey = tsKey(accountNumber);
        String amtKey = amtKey(accountNumber);
        double nowMillis = now.toEpochMilli();
        double cutoff24h = now.minus(WINDOW_24H).toEpochMilli();

        redis.opsForZSet().add(tsKey, transactionId, nowMillis);
        redis.opsForHash().put(amtKey, transactionId, amount.toPlainString());

        // Prune anything older than the largest window we track, from both structures.
        Set<String> expired = redis.opsForZSet().rangeByScore(tsKey, 0, cutoff24h);
        redis.opsForZSet().removeRangeByScore(tsKey, 0, cutoff24h);
        if (expired != null && !expired.isEmpty()) {
            redis.opsForHash().delete(amtKey, expired.toArray());
        }

        redis.expire(tsKey, KEY_TTL);
        redis.expire(amtKey, KEY_TTL);

        double cutoff1h = now.minus(WINDOW_1H).toEpochMilli();
        Long count24h = redis.opsForZSet().zCard(tsKey);
        Long count1h = redis.opsForZSet().count(tsKey, cutoff1h, nowMillis);
        Set<String> ids1h = redis.opsForZSet().rangeByScore(tsKey, cutoff1h, nowMillis);

        double sum1h = 0.0;
        if (ids1h != null && !ids1h.isEmpty()) {
            sum1h = redis.opsForHash().multiGet(amtKey, ids1h.stream().map(Object.class::cast).collect(Collectors.toList()))
                    .stream()
                    .filter(v -> v != null)
                    .mapToDouble(v -> Double.parseDouble((String) v))
                    .sum();
        }

        return new VelocitySnapshotDto(
                count1h == null ? 0 : count1h.intValue(),
                count24h == null ? 0 : count24h.intValue(),
                sum1h);
    }
}
