-- Immutable audit trail for referral fraud events.
-- Written by both ingestion-service (REFERRAL_CREATED, FLAGGED, POINTS_AWARDED, POINTS_REJECTED)
-- and admin-service (BLACKLIST_ACTION, WHITELIST_ACTION) since both share the same DB.
CREATE TABLE ingestion.referral_fraud_audit (
    id              BIGSERIAL    PRIMARY KEY,
    event_type      TEXT         NOT NULL,
    referrer_email  TEXT         NOT NULL,
    referee_email   TEXT,
    ip_hash         TEXT,
    delta           INT,
    total_points    INT,
    reason          TEXT,
    admin_user      TEXT,
    idempotency_key UUID         UNIQUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_fraud_audit_referrer ON ingestion.referral_fraud_audit(referrer_email);
CREATE INDEX idx_fraud_audit_created  ON ingestion.referral_fraud_audit(created_at);
CREATE INDEX idx_fraud_audit_type     ON ingestion.referral_fraud_audit(event_type);
