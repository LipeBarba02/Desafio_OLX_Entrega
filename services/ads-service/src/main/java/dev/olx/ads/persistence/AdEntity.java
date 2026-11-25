package dev.olx.ads.persistence;

import dev.olx.ads.domain.AdDraft;
import dev.olx.ads.domain.AdStatus;
import dev.olx.ads.domain.Category;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;

@Entity @Table(name="ads")
public class AdEntity {
    @Id private UUID id;
    @Column(nullable=false,length=100) private String title;
    @Column(nullable=false,columnDefinition="text") private String description;
    @Column(nullable=false,precision=19,scale=2) private BigDecimal price;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=32) private Category category;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=32) private AdStatus status;
    @Column(name="rejection_reason",length=100) private String rejectionReason;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @ElementCollection(fetch=FetchType.LAZY) @CollectionTable(name="ad_images",joinColumns=@JoinColumn(name="ad_id"))
    @BatchSize(size=64)
    @OrderColumn(name="position") @Column(name="image_url",nullable=false,columnDefinition="text")
    private List<String> images = new ArrayList<>();
    protected AdEntity() { }
    public AdEntity(UUID id, AdDraft draft, Instant createdAt) {
        this.id=id; this.title=draft.title(); this.description=draft.description(); this.price=draft.price();
        this.category=draft.category(); this.images=new ArrayList<>(draft.images()); this.createdAt=createdAt;
        this.status=AdStatus.PENDING_MODERATION;
    }
    public UUID getId(){return id;} public String getTitle(){return title;} public String getDescription(){return description;}
    public BigDecimal getPrice(){return price;} public Category getCategory(){return category;} public AdStatus getStatus(){return status;}
    public String getRejectionReason(){return rejectionReason;} public Instant getCreatedAt(){return createdAt;}
    public List<String> getImages(){return List.copyOf(images);}
    public void applyModeration(AdStatus status,String reason){
        if (status != AdStatus.APPROVED && status != AdStatus.REJECTED) throw new IllegalArgumentException("Final moderation status required");
        this.status=status; this.rejectionReason=status==AdStatus.REJECTED?reason:null;
    }
}
