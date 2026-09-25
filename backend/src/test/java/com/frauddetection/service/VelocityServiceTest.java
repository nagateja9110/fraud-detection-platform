package com.frauddetection.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.frauddetection.dto.VelocitySnapshotDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class VelocityServiceTest {

    static GenericContainer<?> redisContainer =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    static LettuceConnectionFactory connectionFactory;
    static VelocityService velocityService;

    @BeforeAll
    static void setUp() {
        redisContainer.start();
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(redisContainer.getHost(), redisContainer.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
        StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        velocityService = new VelocityService(redisTemplate);
    }

    @AfterAll
    static void tearDown() {
        connectionFactory.destroy();
        redisContainer.stop();
    }

    @Test
    void firstTransactionHasCountOfOne() {
        String account = "acct-" + UUID.randomUUID();
        VelocitySnapshotDto snapshot = velocityService.recordAndSnapshot(
                account, UUID.randomUUID().toString(), BigDecimal.valueOf(50), Instant.now());

        assertThat(snapshot.txnCount1h()).isEqualTo(1);
        assertThat(snapshot.txnCount24h()).isEqualTo(1);
        assertThat(snapshot.amountSum1h()).isEqualTo(50.0);
    }

    @Test
    void burstOfTransactionsIncreasesVelocity() {
        String account = "acct-" + UUID.randomUUID();
        Instant now = Instant.now();

        for (int i = 0; i < 5; i++) {
            velocityService.recordAndSnapshot(account, UUID.randomUUID().toString(), BigDecimal.valueOf(100), now);
        }
        VelocitySnapshotDto snapshot = velocityService.recordAndSnapshot(
                account, UUID.randomUUID().toString(), BigDecimal.valueOf(100), now);

        assertThat(snapshot.txnCount1h()).isEqualTo(6);
        assertThat(snapshot.txnCount24h()).isEqualTo(6);
        assertThat(snapshot.amountSum1h()).isEqualTo(600.0);
    }

    @Test
    void transactionsOutsideOneHourWindowDoNotCountTowardTxnCount1h() {
        String account = "acct-" + UUID.randomUUID();
        Instant twoHoursAgo = Instant.now().minusSeconds(2 * 3600);
        Instant now = Instant.now();

        velocityService.recordAndSnapshot(account, UUID.randomUUID().toString(), BigDecimal.valueOf(75), twoHoursAgo);
        VelocitySnapshotDto snapshot = velocityService.recordAndSnapshot(
                account, UUID.randomUUID().toString(), BigDecimal.valueOf(25), now);

        assertThat(snapshot.txnCount1h()).isEqualTo(1);
        assertThat(snapshot.txnCount24h()).isEqualTo(2);
        assertThat(snapshot.amountSum1h()).isEqualTo(25.0);
    }
}
