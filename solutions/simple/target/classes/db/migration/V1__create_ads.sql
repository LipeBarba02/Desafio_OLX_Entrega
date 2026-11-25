CREATE TABLE ads (
    id UUID PRIMARY KEY,
    title VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    price NUMERIC(19,2) NOT NULL CHECK (price > 0),
    category VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('APPROVED', 'REJECTED')),
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
