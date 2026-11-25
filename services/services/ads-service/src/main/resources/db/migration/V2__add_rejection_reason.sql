-- Bring databases created from an earlier V1 schema up to the current entity model.
-- Safe for fresh databases where V1 already created this column.
ALTER TABLE ads
    ADD COLUMN IF NOT EXISTS rejection_reason VARCHAR(100);
