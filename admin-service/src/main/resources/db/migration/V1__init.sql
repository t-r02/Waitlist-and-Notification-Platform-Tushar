CREATE TABLE admin.waitlist_entries (
    id           BIGSERIAL PRIMARY KEY,
    ingestion_id BIGINT UNIQUE NOT NULL,
    email        TEXT UNIQUE NOT NULL,
    name         TEXT,
    company      TEXT,
    status       TEXT NOT NULL DEFAULT 'PENDING',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    version      BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE admin.admin_users (
    id       BIGSERIAL PRIMARY KEY,
    username TEXT UNIQUE NOT NULL,
    password TEXT NOT NULL
);

CREATE TABLE admin.status_audit_log (
    id         BIGSERIAL PRIMARY KEY,
    entry_id   BIGINT NOT NULL,
    old_status TEXT,
    new_status TEXT NOT NULL,
    changed_by TEXT NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE admin.outbox (
    id             BIGSERIAL PRIMARY KEY,
    aggregate_type TEXT NOT NULL,
    aggregate_id   TEXT NOT NULL,
    event_type     TEXT NOT NULL,
    payload        JSONB NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ
);

CREATE INDEX idx_entries_status ON admin.waitlist_entries(status);
CREATE INDEX idx_audit_entry    ON admin.status_audit_log(entry_id);
CREATE INDEX idx_outbox_unpublished ON admin.outbox(published_at) WHERE published_at IS NULL;
