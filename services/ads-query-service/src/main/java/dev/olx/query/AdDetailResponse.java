package dev.olx.query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Detail cache payload hydrated from the command service; images are intentionally absent from moderation events. */
public record AdDetailResponse(UUID id,String title,String description,BigDecimal price,String category,
                               List<String> images,String status,String rejectionReason,Instant createdAt){ }
