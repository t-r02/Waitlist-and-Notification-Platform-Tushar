ALTER TABLE notification.notification_log
    ADD COLUMN event_id UUID UNIQUE NOT NULL DEFAULT gen_random_uuid();

-- Remove the transient DEFAULT so future inserts must supply event_id explicitly
ALTER TABLE notification.notification_log
    ALTER COLUMN event_id DROP DEFAULT;
