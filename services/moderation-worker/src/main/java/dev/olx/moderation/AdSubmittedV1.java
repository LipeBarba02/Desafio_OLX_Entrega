package dev.olx.moderation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AdSubmittedV1(UUID eventId,int schemaVersion,Instant occurredAt,UUID adId,String title,
                            String description,BigDecimal price,String category){ }
