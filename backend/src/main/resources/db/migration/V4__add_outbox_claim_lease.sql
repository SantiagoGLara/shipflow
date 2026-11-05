-- Permite recuperar eventos que quedaron PROCESSING tras una caída del backend.
ALTER TABLE outbox_events
    ADD COLUMN claimed_at TIMESTAMPTZ;

CREATE INDEX idx_outbox_processing_claimed_at
    ON outbox_events(claimed_at)
    WHERE status = 'PROCESSING';
