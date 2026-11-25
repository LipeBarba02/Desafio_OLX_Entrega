package dev.olx.query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SearchDocument(UUID adId,String title,String description,BigDecimal price,String category,
                             Instant createdAt){ }
