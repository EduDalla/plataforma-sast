CREATE TABLE analysis_report_snapshots (
    id UUID PRIMARY KEY,
    analysis_id UUID NOT NULL REFERENCES analyses(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES app_users(id),
    version INTEGER NOT NULL,
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    analysis_status VARCHAR(32) NOT NULL,
    coverage_status VARCHAR(32) NOT NULL,
    schema_version VARCHAR(32) NOT NULL,
    snapshot_hash VARCHAR(64) NOT NULL,
    report_payload TEXT NOT NULL,
    CONSTRAINT analysis_report_snapshots_version_uq UNIQUE (analysis_id, version)
);

CREATE INDEX analysis_report_snapshots_owner_idx
    ON analysis_report_snapshots(user_id, analysis_id, version DESC);
