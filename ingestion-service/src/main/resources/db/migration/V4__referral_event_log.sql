-- Idempotency log for StatusChangedEvents consumed by ingestion-service.
-- The unique constraint on event_id ensures that a replayed Kafka message
-- (same UUID) cannot award or reverse points a second time.
CREATE TABLE ingestion.referral_event_log (
    id           BIGSERIAL    PRIMARY KEY,
    event_id     UUID         NOT NULL,
    action       TEXT         NOT NULL,   -- AWARD | REVERSE | NOOP
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_referral_event_log_event_id UNIQUE (event_id)
);

CREATE INDEX idx_referral_event_log_event_id ON ingestion.referral_event_log(event_id);
