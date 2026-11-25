package dev.olx.ads.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import dev.olx.ads.persistence.OutboxEventEntity;
import dev.olx.ads.persistence.OutboxEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class OutboxRelayTest {
    private static final Instant NOW = Instant.parse("2026-11-24T12:00:00Z");

    @Test void publishesClaimedEventThenMarksItPublished() {
        OutboxEventRepository outbox = mock(OutboxEventRepository.class);
        @SuppressWarnings("unchecked") KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        OutboxEventEntity event = event();
        when(outbox.findEligible(eq(NOW), any())).thenReturn(List.of(event));
        when(outbox.claim(eq(event.getId()), eq(NOW), any())).thenReturn(1);
        when(kafka.send(anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(null));

        new OutboxRelay(outbox, kafka, Clock.fixed(NOW, ZoneOffset.UTC), "ads.submitted.v1")
            .publishPendingEvents();

        verify(kafka).send("ads.submitted.v1", event.getAggregateId().toString(), event.getPayload());
        verify(outbox).markPublished(event.getId(), NOW);
        verify(outbox, never()).scheduleRetry(any(), any(), anyString());
    }

    @Test void skipsKafkaWhenAnotherRelayOwnsTheClaim() {
        OutboxEventRepository outbox = mock(OutboxEventRepository.class);
        @SuppressWarnings("unchecked") KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        OutboxEventEntity event = event();
        when(outbox.findEligible(eq(NOW), any())).thenReturn(List.of(event));
        when(outbox.claim(eq(event.getId()), eq(NOW), any())).thenReturn(0);

        new OutboxRelay(outbox, kafka, Clock.fixed(NOW, ZoneOffset.UTC), "topic").publishPendingEvents();

        verifyNoInteractions(kafka);
        verify(outbox, never()).markPublished(any(), any());
    }

    @Test void schedulesRetryWhenKafkaFutureFails() {
        OutboxEventRepository outbox = mock(OutboxEventRepository.class);
        @SuppressWarnings("unchecked") KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        OutboxEventEntity event = event();
        when(outbox.findEligible(eq(NOW), any())).thenReturn(List.of(event));
        when(outbox.claim(eq(event.getId()), eq(NOW), any())).thenReturn(1);
        when(kafka.send(anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));

        new OutboxRelay(outbox, kafka, Clock.fixed(NOW, ZoneOffset.UTC), "topic").publishPendingEvents();

        verify(outbox).scheduleRetry(eq(event.getId()), any(Instant.class), contains("broker unavailable"));
        verify(outbox, never()).markPublished(any(), any());
    }

    private static OutboxEventEntity event() {
        Instant created = NOW.minusSeconds(1);
        return OutboxEventEntity.pending(UUID.randomUUID(), UUID.randomUUID(), "AdSubmittedV1", 1,
            "{\"eventId\":\"test\"}", created);
    }
}
