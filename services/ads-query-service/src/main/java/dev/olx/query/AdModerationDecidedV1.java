package dev.olx.query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AdModerationDecidedV1(UUID eventId,int schemaVersion,Instant occurredAt,UUID adId,String status,
                                    String rejectionReason,Instant adCreatedAt,String title,String description,String category,
                                    BigDecimal price){ }
