package dev.olx.ads.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA row written in the same transaction as an aggregate change. */
@Entity
@Table(name = "outbox_events")
public class OutboxEventEntity {
    public enum Status { PENDING, IN_FLIGHT, PUBLISHED }

    @Id
    private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    protected OutboxEventEntity() { }

    public static OutboxEventEntity pending(
        UUID eventId,
        UUID aggregateId,
        String eventType,
        int schemaVersion,
        String payload,
        Instant createdAt
    ) {
        OutboxEventEntity event = new OutboxEventEntity();
        event.id = eventId;
        event.aggregateId = aggregateId;
        event.eventType = eventType;
        event.schemaVersion = schemaVersion;
        event.payload = payload;
        event.status = Status.PENDING.name();
        event.attemptCount = 0;
        event.createdAt = createdAt;
        event.nextAttemptAt = createdAt;
        return event;
    }

    public UUID getId() { return id; }
    public UUID getAggregateId() { return aggregateId; }
    public String getPayload() { return payload; }
    public int getAttemptCount() { return attemptCount; }
    public String getStatus() { return status; }
}
