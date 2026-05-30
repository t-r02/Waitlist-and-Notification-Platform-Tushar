-- Email verification columns for waitlist_entries.
-- verified:                     false until user clicks the link in their inbox
-- verification_token:           64-char hex token e-mailed to the user
-- verification_token_expires_at: 24-hour TTL for the token
ALTER TABLE ingestion.waitlist_entries
    ADD COLUMN verified                      BOOLEAN     NOT NULL DEFAULT false,
    ADD COLUMN verification_token            VARCHAR(64) UNIQUE,
    ADD COLUMN verification_token_expires_at TIMESTAMPTZ;

-- State machine for referral-point owners: ACTIVE → FLAGGED → BLACKLISTED | WHITELISTED
ALTER TABLE ingestion.referral_points
    ADD COLUMN referrer_status TEXT NOT NULL DEFAULT 'ACTIVE';

CREATE INDEX idx_waitlist_entries_token
    ON ingestion.waitlist_entries(verification_token)
    WHERE verification_token IS NOT NULL;
