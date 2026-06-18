CREATE TABLE outbox_events (
    event_id       UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id   VARCHAR(100) NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        JSONB NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at   TIMESTAMPTZ NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    claimed_at     TIMESTAMPTZ NULL,
    claim_until    TIMESTAMPTZ NULL,
    attempt_count  INT NOT NULL DEFAULT 0,
    last_error     TEXT NULL,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_outbox_events_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'PROCESSED', 'FAILED', 'FINAL_FAILED'))
);

CREATE INDEX idx_outbox_events_claimable
    ON outbox_events (status, next_attempt_at, claim_until, created_at)
    WHERE processed_at IS NULL;
