ALTER TABLE outbox_events
    ADD COLUMN attempt_count    INT          NOT NULL DEFAULT 0,
    ADD COLUMN last_attempt_at  TIMESTAMP    NULL,
    ADD COLUMN status           VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    ADD COLUMN aggregate_id     VARCHAR(64)  NOT NULL DEFAULT '';

CREATE INDEX idx_outbox_events_status_created
    ON outbox_events (status, created_at);
