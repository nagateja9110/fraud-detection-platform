package com.frauddetection;

import static org.assertj.core.api.Assertions.assertThat;

import com.frauddetection.dto.TransactionRequest;
import com.frauddetection.entity.Channel;
import com.frauddetection.entity.MerchantCategory;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * End-to-end smoke test for the transaction ingestion flow: real Postgres +
 * real Redis (Testcontainers), with the ml-service call pointed at a tiny
 * in-JVM HTTP stub so this doesn't depend on the Python service being up.
 * Model accuracy itself is covered by ml-service/tests (pytest).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TransactionApiIntegrationTest {

    @org.testcontainers.junit.jupiter.Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @org.testcontainers.junit.jupiter.Container
    static GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    static HttpServer mlStub;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("fraud.ml-service.url", () -> "http://localhost:" + mlStub.getAddress().getPort());
    }

    @BeforeAll
    static void startMlStub() throws IOException {
        mlStub = HttpServer.create(new InetSocketAddress(0), 0);
        mlStub.createContext("/predict", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String txnId = extract(body, "\"transaction_id\":\"([^\"]+)\"");
            double amount = Double.parseDouble(extract(body, "\"amount\":([0-9.]+)"));

            boolean fraudLike = amount > 500;
            String riskLevel = fraudLike ? "CRITICAL" : "LOW";
            double probability = fraudLike ? 0.97 : 0.01;
            String riskFactors = fraudLike
                    ? "[{\"feature\":\"amount\",\"description\":\"Transaction amount\",\"contribution\":0.8}]"
                    : "[]";

            String response = """
                    {"transaction_id":"%s","fraud_probability":%s,"risk_level":"%s",
                    "top_risk_factors":%s,"model_version":"test-stub","latency_ms":1.0}
                    """.formatted(txnId, probability, riskLevel, riskFactors);

            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        mlStub.start();
    }

    @AfterAll
    static void stopMlStub() {
        mlStub.stop(0);
    }

    private static String extract(String body, String pattern) {
        Matcher m = Pattern.compile(pattern).matcher(body);
        if (!m.find()) {
            throw new IllegalStateException("Pattern not found: " + pattern + " in " + body);
        }
        return m.group(1);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void normalTransactionIsApproved() {
        TransactionRequest request = new TransactionRequest(
                "acct-normal-1", "Jane Doe", "CHECKING", "US",
                "device-abc", "MOBILE",
                BigDecimal.valueOf(42.50), "USD",
                MerchantCategory.GROCERY, Channel.POS, "US", null);

        ResponseEntity<TransactionResponseTestView> response =
                restTemplate.postForEntity("/api/transactions", request, TransactionResponseTestView.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().riskLevel()).isEqualTo("LOW");
        assertThat(response.getBody().decision()).isEqualTo("APPROVE");
    }

    @Test
    void largeTransactionBurstEscalatesToDecline() {
        String account = "acct-burst-1";
        TransactionRequest first = new TransactionRequest(
                account, "John Roe", "CHECKING", "US",
                "device-new", "DESKTOP",
                BigDecimal.valueOf(900), "USD",
                MerchantCategory.GIFT_CARD, Channel.ONLINE, "RO", null);

        ResponseEntity<TransactionResponseTestView> response =
                restTemplate.postForEntity("/api/transactions", first, TransactionResponseTestView.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().riskLevel()).isEqualTo("CRITICAL");
        assertThat(response.getBody().decision()).isEqualTo("DECLINE");

        ResponseEntity<String> summary =
                restTemplate.getForEntity("/api/accounts/" + account + "/risk-summary", String.class);
        assertThat(summary.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(summary.getBody()).contains("\"flaggedTransactions\":1");
    }

    /** Minimal view of TransactionResponse for assertions (avoids coupling test JSON parsing to every field). */
    record TransactionResponseTestView(String riskLevel, String decision) {
    }
}
