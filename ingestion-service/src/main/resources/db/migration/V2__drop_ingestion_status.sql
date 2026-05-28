-- Ingestion service no longer owns entry status.
-- Status is the sole responsibility of admin-service (admin.waitlist_entries.status).
-- The ingestion projection only needs to know that a signup happened; the admin
-- projection learns status transitions via waitlist.status-changed Kafka events.
ALTER TABLE ingestion.waitlist_entries DROP COLUMN status;
