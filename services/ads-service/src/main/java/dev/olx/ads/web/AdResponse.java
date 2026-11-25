package dev.olx.ads.web;

import dev.olx.ads.domain.AdStatus;
import dev.olx.ads.persistence.AdEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdResponse(UUID id,String title,String description,BigDecimal price,String category,List<String> images,
                         AdStatus status,String rejectionReason,Instant createdAt) {
    public static AdResponse from(AdEntity ad){return new AdResponse(ad.getId(),ad.getTitle(),ad.getDescription(),ad.getPrice(),ad.getCategory().code(),ad.getImages(),ad.getStatus(),ad.getRejectionReason(),ad.getCreatedAt());}
}
