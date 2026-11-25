package dev.olx.ads.application;

import java.time.Instant;
import java.util.UUID;

public record AdModerationDecidedV1(UUID eventId, int schemaVersion, Instant occurredAt, UUID adId,
                                    String status, String rejectionReason, Instant adCreatedAt, String title,
                                    String description, String category, java.math.BigDecimal price) { }
