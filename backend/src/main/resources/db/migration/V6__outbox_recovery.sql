-- Recuperación segura de eventos que quedaron PROCESSING tras una caída.
ALTER TABLE outbox_events
    ADD COLUMN processing_started_at TIMESTAMPTZ;

UPDATE outbox_events
SET status = 'PENDING', processing_started_at = NULL
WHERE status = 'PROCESSING';

CREATE INDEX idx_outbox_processing_started
    ON outbox_events(processing_started_at)
    WHERE status = 'PROCESSING';
