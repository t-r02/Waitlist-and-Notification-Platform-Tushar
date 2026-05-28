-- Add fraud-detection flag to referral_points.
-- Flagged referrers are excluded from the public leaderboard.
ALTER TABLE ingestion.referral_points ADD COLUMN flagged BOOLEAN NOT NULL DEFAULT false;

-- Fingerprint table: tracks how many referees a given referrer has submitted
-- from the same IP hash within a rolling 24-hour window.
-- If count exceeds the threshold the referrer is flagged automatically.
CREATE TABLE ingestion.referrals_fingerprint (
    id             BIGSERIAL PRIMARY KEY,
    referrer_email TEXT NOT NULL,
    ip_hash        TEXT NOT NULL,
    count          INT NOT NULL DEFAULT 1,
    window_start   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (referrer_email, ip_hash)
);

CREATE INDEX idx_fingerprint_referrer ON ingestion.referrals_fingerprint(referrer_email);
