CREATE TABLE ingestion.waitlist_entries (
    id           BIGSERIAL PRIMARY KEY,
    email        TEXT UNIQUE NOT NULL,
    name         TEXT,
    company      TEXT,
    status       TEXT NOT NULL DEFAULT 'PENDING',
    referral_code TEXT UNIQUE NOT NULL,
    referred_by  TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    version      BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE ingestion.referrals (
    id             BIGSERIAL PRIMARY KEY,
    referrer_email TEXT NOT NULL,
    referee_email  TEXT NOT NULL UNIQUE,
    converted      BOOLEAN NOT NULL DEFAULT false,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ingestion.referral_points (
    id     BIGSERIAL PRIMARY KEY,
    email  TEXT UNIQUE NOT NULL,
    points INT NOT NULL DEFAULT 0,
    badge  TEXT
);

CREATE TABLE ingestion.outbox (
    id             BIGSERIAL PRIMARY KEY,
    aggregate_type TEXT NOT NULL,
    aggregate_id   TEXT NOT NULL,
    event_type     TEXT NOT NULL,
    payload        JSONB NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ
);

CREATE INDEX idx_referrals_referrer      ON ingestion.referrals(referrer_email);
CREATE INDEX idx_outbox_unpublished      ON ingestion.outbox(published_at) WHERE published_at IS NULL;
