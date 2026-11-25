package dev.olx.ads.application;

import dev.olx.ads.persistence.OutboxEventEntity;
import dev.olx.ads.persistence.OutboxEventRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Bridges a committed PostgreSQL Outbox row to Kafka.
 *
 * <p>The row is claimed before sending and marked PUBLISHED only after Kafka
 * acknowledges. A crash between those steps may publish twice; downstream
 * consumers therefore use stable event/aggregate identifiers and idempotent
 * state changes. The relay favors recoverable delivery over at-most-once loss.</p>
 */
@Component
public class OutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private static final int BATCH_SIZE = 100;
    private static final Duration LEASE = Duration.ofSeconds(120);
    private static final long SEND_TIMEOUT_SECONDS = 35;
    private static final long MAX_RETRY_SECONDS = 60;

    private final OutboxEventRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final Clock clock;
    private final String topic;

    public OutboxRelay(
        OutboxEventRepository outbox,
        KafkaTemplate<String, String> kafka,
        Clock clock,
        @Value("${app.kafka.topics.ad-submitted:ads.submitted.v1}") String topic
    ) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.clock = clock;
        this.topic = topic;
    }

    /** Spring invokes this after the configured delay; work duration is added to the delay. */
    @Scheduled(fixedDelayString = "${outbox.poll-delay-ms:1000}")
    public void publishPendingEvents() {
        Instant now = clock.instant();
        List<OutboxEventEntity> candidates = outbox.findEligible(now, PageRequest.of(0, BATCH_SIZE));
        List<OutboxEventEntity> claimed = new ArrayList<>();

        for (OutboxEventEntity event : candidates) {
            Instant claimTime = clock.instant();
            int reservation = outbox.claim(event.getId(), claimTime, claimTime.plus(LEASE));
            if (reservation == 1) {
                claimed.add(event);
            }
        }
        publishBatch(claimed);
    }

    private void publishBatch(List<OutboxEventEntity> claimed) {
        List<OutboxEventEntity> sending = new ArrayList<>();
        List<CompletableFuture<SendResult<String, String>>> acknowledgements = new ArrayList<>();

        // KafkaTemplate.send starts asynchronous I/O. We start the bounded batch before waiting.
        for (OutboxEventEntity event : claimed) {
            try {
                acknowledgements.add(kafka.send(topic, event.getAggregateId().toString(), event.getPayload()));
                sending.add(event);
            } catch (RuntimeException failure) {
                scheduleRetry(event, failure);
            }
        }
        if (sending.isEmpty()) {
            return;
        }

        CompletableFuture<?>[] futures = acknowledgements.toArray(CompletableFuture[]::new);
        try {
            CompletableFuture.allOf(futures).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            for (OutboxEventEntity event : sending) {
                outbox.markPublished(event.getId(), clock.instant());
            }
        } catch (InterruptedException exception) {
            sending.forEach(event -> scheduleRetry(event, exception));
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException | RuntimeException exception) {
            // Some records may already have reached Kafka. Retrying the batch can duplicate them;
            // it preserves the no-loss preference and requires idempotent consumers.
            sending.forEach(event -> scheduleRetry(event, exception));
        }
    }

    private void scheduleRetry(OutboxEventEntity event, Exception failure) {
        int attempt = event.getAttemptCount() + 1;
        int exponent = Math.min(Math.max(attempt - 1, 0), 6);
        long delayCeilingSeconds = Math.min(1L << exponent, MAX_RETRY_SECONDS);
        long delaySeconds = ThreadLocalRandom.current().nextLong(1, delayCeilingSeconds + 1);

        String error = failure.getMessage();
        if (error == null || error.isBlank()) {
            error = failure.getClass().getSimpleName();
        }
        if (error.length() > 1000) {
            error = error.substring(0, 1000);
        }

        int updated = outbox.scheduleRetry(
            event.getId(),
            clock.instant().plusSeconds(delaySeconds),
            error
        );
        if (updated == 0) {
            log.warn("Outbox retry state was not updated for event {}", event.getId());
        }
    }
}
