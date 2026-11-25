package dev.olx.moderation;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

/** Consumes durable submission events, invokes the mock within quota, and emits a decision event. */
@Component
public class ModerationConsumer {
    private static final Logger log = LoggerFactory.getLogger(ModerationConsumer.class);
    private static final long DECISION_PUBLISH_TIMEOUT_SECONDS = 15;

    private final ObjectMapper json;
    private final GlobalQuotaGate quota;
    private final ModerationHttpClient moderationClient;
    private final ModerationDecisionRepository decisions;
    private final KafkaTemplate<String, String> kafka;
    private final Clock clock;
    private final String decisionTopic;

    public ModerationConsumer(
        ObjectMapper json,
        GlobalQuotaGate quota,
        ModerationHttpClient moderationClient,
        ModerationDecisionRepository decisions,
        KafkaTemplate<String, String> kafka,
        Clock clock,
        @Value("${app.kafka.topics.ad-moderation-decided}") String decisionTopic
    ) {
        this.json = json;
        this.quota = quota;
        this.moderationClient = moderationClient;
        this.decisions = decisions;
        this.kafka = kafka;
        this.clock = clock;
        this.decisionTopic = decisionTopic;
    }

    /**
     * Spring Kafka forwards failures through bounded retry topics and then the DLT.
     * The Redis gate also applies to retries because each attempt calls acquire().
     */
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 1000, maxDelay = 30000, multiplier = 2.0, random = true),
        dltTopicSuffix = ".dlt"
    )
    @KafkaListener(
        topics = "${app.kafka.topics.ad-submitted}",
        groupId = "moderation-worker",
        concurrency = "${moderation.consumer-concurrency:1}"
    )
    public void onAdSubmitted(String payload) throws Exception {
        AdSubmittedV1 ad = json.readValue(payload, AdSubmittedV1.class);

        // If a previous delivery already persisted the result, skip the external call.
        ModerationDecisionEntity decision = decisions.findById(ad.eventId()).orElse(null);
        if (decision == null) {
            quota.acquire();
            ModerationHttpClient.Decision response;
            try {
                response = moderationClient.check(ad);
            } catch (HttpClientErrorException.TooManyRequests rateLimited) {
                waitForRetryAfter(rateLimited);
                throw rateLimited;
            }
            decision = storeDecision(ad, response);
        }

        AdModerationDecidedV1 event = new AdModerationDecidedV1(
            decision.getEventId(),
            1,
            decision.getDecidedAt(),
            decision.getAdId(),
            decision.getStatus(),
            decision.getRejectionReason(),
            ad.occurredAt(),
            ad.title(),
            ad.description(),
            ad.category(),
            ad.price()
        );

        kafka.send(decisionTopic, ad.adId().toString(), json.writeValueAsString(event))
            .get(DECISION_PUBLISH_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // If the process crashes before Kafka commits the input offset, this event can be sent again.
        // Decision consumers overwrite state by adId and OpenSearch upserts by adId, so they converge.
    }

    /** The mock documents Retry-After as integer seconds; cap blocking time at 30 seconds. */
    private void waitForRetryAfter(HttpClientErrorException.TooManyRequests failure) {
        String header = failure.getResponseHeaders() == null
            ? null
            : failure.getResponseHeaders().getFirst("Retry-After");
        if (header == null) {
            return;
        }
        try {
            long seconds = Math.max(0, Math.min(Long.parseLong(header), 30));
            TimeUnit.SECONDS.sleep(seconds);
        } catch (NumberFormatException ignored) {
            // Invalid values fall back to the retry-topic backoff.
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while honoring moderation Retry-After", interrupted);
        }
    }

    private ModerationDecisionEntity storeDecision(
        AdSubmittedV1 ad,
        ModerationHttpClient.Decision response
    ) {
        try {
            // JpaRepository.saveAndFlush opens/commits a short transaction around this insert.
            return decisions.saveAndFlush(new ModerationDecisionEntity(
                ad.eventId(),
                ad.adId(),
                response.status(),
                response.rejectionReason(),
                clock.instant()
            ));
        } catch (DataIntegrityViolationException duplicateDelivery) {
            // A concurrent rebalance may race; the unique eventId row is the idempotency key.
            return decisions.findById(ad.eventId()).orElseThrow(() -> duplicateDelivery);
        }
    }

    @DltHandler
    public void onDeadLetter(String payload) {
        String eventId = "unparsed";
        try {
            eventId = json.readTree(payload).path("eventId").asText("unparsed");
        } catch (Exception ignored) {
            // Do not log the full event: descriptions can contain user-provided data.
        }
        log.error("Moderation event routed to DLT; manual inspection/replay required. eventId={}", eventId);
    }
}
