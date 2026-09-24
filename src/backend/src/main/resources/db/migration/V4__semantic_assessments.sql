ALTER TABLE analyses ADD COLUMN semantic_status VARCHAR(32) NOT NULL DEFAULT 'NOT_APPLICABLE';
ALTER TABLE analyses ADD COLUMN semantic_model VARCHAR(100);
ALTER TABLE analyses ADD COLUMN prompt_version VARCHAR(32);
UPDATE analyses SET semantic_status = 'DEGRADED' WHERE EXISTS (SELECT 1 FROM findings WHERE findings.analysis_id = analyses.id);
CREATE TABLE ai_assessments (
    finding_id UUID PRIMARY KEY REFERENCES findings(id) ON DELETE CASCADE,
    model VARCHAR(100) NOT NULL,
    prompt_version VARCHAR(32) NOT NULL,
    confidence DOUBLE PRECISION NOT NULL,
    suggested_severity VARCHAR(16) NOT NULL,
    likely_false_positive BOOLEAN NOT NULL,
    rationale VARCHAR(500) NOT NULL,
    remediation VARCHAR(1000) NOT NULL
);
