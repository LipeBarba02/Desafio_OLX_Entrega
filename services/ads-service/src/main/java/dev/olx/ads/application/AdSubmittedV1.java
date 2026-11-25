package dev.olx.ads.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Versioned integration contract. Image URLs travel for read projection; moderation mock does not receive them. */
public record AdSubmittedV1(UUID eventId, int schemaVersion, Instant occurredAt, UUID adId,
                            String title, String description, BigDecimal price, String category) { }
