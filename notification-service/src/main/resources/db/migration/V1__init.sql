CREATE TABLE notification.notification_log (
    id        BIGSERIAL PRIMARY KEY,
    event_key TEXT UNIQUE NOT NULL,
    email     TEXT NOT NULL,
    type      TEXT NOT NULL,
    sent_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
