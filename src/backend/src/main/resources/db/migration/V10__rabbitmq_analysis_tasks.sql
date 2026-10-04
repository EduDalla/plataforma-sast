ALTER TABLE analyses ADD COLUMN commit_sha VARCHAR(40);
ALTER TABLE analyses ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE analyses ADD COLUMN lease_owner VARCHAR(100);
ALTER TABLE analyses ADD COLUMN lease_until TIMESTAMP WITH TIME ZONE;
ALTER TABLE analyses ADD COLUMN next_attempt_at TIMESTAMP WITH TIME ZONE;
CREATE INDEX analyses_expired_lease_idx ON analyses(status, lease_until);

CREATE TABLE analysis_outbox (
    id UUID PRIMARY KEY,
    analysis_id UUID NOT NULL REFERENCES analyses(id) ON DELETE CASCADE,
    event_type VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    available_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(500)
);
CREATE INDEX analysis_outbox_ready_idx ON analysis_outbox(published_at, available_at, created_at);
CREATE INDEX analysis_outbox_analysis_idx ON analysis_outbox(analysis_id, published_at);
