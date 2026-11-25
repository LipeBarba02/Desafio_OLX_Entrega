package dev.olx.simple.web;

import dev.olx.simple.domain.ModerationStatus;
import dev.olx.simple.persistence.AdEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdResponse(UUID id, String title, String description, BigDecimal price,
                         String category, List<String> images, ModerationStatus status,
                         String rejectionReason, Instant createdAt) {
    public static AdResponse from(AdEntity ad) {
        return new AdResponse(ad.getId(), ad.getTitle(), ad.getDescription(), ad.getPrice(),
            ad.getCategory().code(), ad.getImages(), ad.getStatus(), ad.getRejectionReason(), ad.getCreatedAt());
    }
}
