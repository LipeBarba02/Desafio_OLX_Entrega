CREATE TABLE ads (
    id UUID PRIMARY KEY,
    title VARCHAR(100) NOT NULL CHECK (char_length(title) BETWEEN 10 AND 100),
    description TEXT NOT NULL,
    price NUMERIC(19,2) NOT NULL CHECK (price > 0),
    category VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN ('PENDING_MODERATION','APPROVED','REJECTED')),
    rejection_reason VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE ad_images (
    ad_id UUID NOT NULL REFERENCES ads(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    image_url TEXT NOT NULL,
    PRIMARY KEY (ad_id, position)
);
CREATE INDEX idx_ads_approved_created ON ads(status, created_at DESC);
CREATE INDEX idx_ads_approved_category_created ON ads(status, category, created_at DESC);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    schema_version INTEGER NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','IN_FLIGHT','PUBLISHED')),
    attempt_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    lease_until TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    last_error TEXT
);
CREATE INDEX idx_outbox_pending ON outbox_events(status,next_attempt_at,created_at);
CREATE INDEX idx_outbox_lease ON outbox_events(status,lease_until,created_at);
