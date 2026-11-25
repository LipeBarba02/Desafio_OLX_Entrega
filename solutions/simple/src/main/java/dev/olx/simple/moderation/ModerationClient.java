package dev.olx.simple.moderation;

import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Synchronous adapter to the challenge's external moderation API. */
@Component
public class ModerationClient {
    private final RestClient client;

    public ModerationClient(@Value("${moderation.base-url}") String baseUrl,
                            @Value("${moderation.connect-timeout-ms:1000}") int connectTimeoutMs,
                            @Value("${moderation.read-timeout-ms:3000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    public Decision check(String title, String description, BigDecimal price, String category) {
        ModerationResponse response = client.post()
            .uri("/v1/moderation/check")
            .body(new ModerationRequest(title, description, price, category))
            .retrieve()
            .body(ModerationResponse.class);
        if (response == null || response.status() == null) {
            throw new ModerationUnavailableException("Moderation returned an empty response");
        }
        return new Decision(response.status(), response.reason() != null ? response.reason() : response.rejection_reason());
    }

    public record ModerationRequest(String title, String description, BigDecimal price, String category) { }
    public record ModerationResponse(String status, String reason, String rejection_reason) { }
    public record Decision(String status, String rejectionReason) { }
}
