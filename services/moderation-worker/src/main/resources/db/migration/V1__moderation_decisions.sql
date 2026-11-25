CREATE TABLE moderation_decisions (
    event_id UUID PRIMARY KEY,
    ad_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL CHECK(status IN ('approved','rejected')),
    rejection_reason VARCHAR(100),
    decided_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_moderation_decisions_ad ON moderation_decisions(ad_id,decided_at DESC);
