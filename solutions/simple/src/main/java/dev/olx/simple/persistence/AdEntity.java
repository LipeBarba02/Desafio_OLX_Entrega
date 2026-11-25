package dev.olx.simple.persistence;

import dev.olx.simple.domain.Category;
import dev.olx.simple.domain.ModerationStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "ads")
public class AdEntity {
    @Id private UUID id;
    @Column(nullable = false, length = 100) private String title;
    @Column(nullable = false, columnDefinition = "text") private String description;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal price;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private Category category;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private ModerationStatus status;
    @Column(name = "rejection_reason", length = 100) private String rejectionReason;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "ad_images", joinColumns = @JoinColumn(name = "ad_id"))
    @OrderColumn(name = "position")
    @Column(name = "image_url", nullable = false, columnDefinition = "text")
    private List<String> images = new ArrayList<>();

    protected AdEntity() { }

    public AdEntity(UUID id, String title, String description, BigDecimal price,
                    Category category, List<String> images, ModerationStatus status,
                    String rejectionReason, Instant createdAt) {
        this.id = id; this.title = title; this.description = description; this.price = price;
        this.category = category; this.images = new ArrayList<>(images); this.status = status;
        this.rejectionReason = rejectionReason; this.createdAt = createdAt;
    }
    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public BigDecimal getPrice() { return price; }
    public Category getCategory() { return category; }
    public List<String> getImages() { return List.copyOf(images); }
    public ModerationStatus getStatus() { return status; }
    public String getRejectionReason() { return rejectionReason; }
    public Instant getCreatedAt() { return createdAt; }
}
