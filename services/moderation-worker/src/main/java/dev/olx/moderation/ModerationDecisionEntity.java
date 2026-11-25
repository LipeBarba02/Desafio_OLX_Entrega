package dev.olx.moderation;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="moderation_decisions",uniqueConstraints=@UniqueConstraint(name="uq_moderation_event",columnNames="event_id"))
public class ModerationDecisionEntity {
    @Id private UUID eventId;
    @Column(name="ad_id",nullable=false) private UUID adId;
    @Column(nullable=false,length=16) private String status;
    @Column(name="rejection_reason",length=100) private String rejectionReason;
    @Column(name="decided_at",nullable=false) private Instant decidedAt;
    protected ModerationDecisionEntity(){ }
    public ModerationDecisionEntity(UUID eventId,UUID adId,String status,String reason,Instant decidedAt){this.eventId=eventId;this.adId=adId;this.status=status;this.rejectionReason=reason;this.decidedAt=decidedAt;}
    public UUID getEventId(){return eventId;} public UUID getAdId(){return adId;} public String getStatus(){return status;}
    public String getRejectionReason(){return rejectionReason;} public Instant getDecidedAt(){return decidedAt;}
}
