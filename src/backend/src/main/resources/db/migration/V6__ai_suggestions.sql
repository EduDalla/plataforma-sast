ALTER TABLE analyses ADD COLUMN suggestion_status VARCHAR(32) NOT NULL DEFAULT 'NOT_APPLICABLE';
ALTER TABLE analyses ADD COLUMN snapshot_hash VARCHAR(64);

CREATE TABLE ai_suggestions (
    id UUID PRIMARY KEY,
    analysis_id UUID NOT NULL REFERENCES analyses(id) ON DELETE CASCADE,
    category VARCHAR(16) NOT NULL,
    title VARCHAR(160) NOT NULL,
    file_name VARCHAR(1000) NOT NULL,
    line_number INTEGER NOT NULL,
    evidence VARCHAR(300) NOT NULL,
    rationale VARCHAR(600) NOT NULL,
    confidence DOUBLE PRECISION NOT NULL,
    recommendation VARCHAR(800) NOT NULL,
    limitations VARCHAR(500) NOT NULL,
    model VARCHAR(100) NOT NULL,
    prompt_version VARCHAR(32) NOT NULL
);
CREATE INDEX ai_suggestions_analysis_idx ON ai_suggestions(analysis_id);
