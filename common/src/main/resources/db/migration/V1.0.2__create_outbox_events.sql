CREATE TABLE outbox_events (
    event_id       UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id   VARCHAR(100) NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        JSONB NOT NULL,
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_at   TIMESTAMP NULL
);

CREATE INDEX idx_outbox_events_unprocessed
    ON outbox_events (created_at)
    WHERE processed_at IS NULL;
