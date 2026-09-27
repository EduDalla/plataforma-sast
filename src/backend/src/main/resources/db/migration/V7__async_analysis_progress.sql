ALTER TABLE analyses ADD COLUMN stage VARCHAR(32) NOT NULL DEFAULT 'COMPLETED';
ALTER TABLE analyses ADD COLUMN files_processed INTEGER NOT NULL DEFAULT 0;
ALTER TABLE analyses ADD COLUMN files_total INTEGER NOT NULL DEFAULT 0;
ALTER TABLE analyses ADD COLUMN failure_stage VARCHAR(32);
ALTER TABLE analyses ADD COLUMN failure_message VARCHAR(500);
UPDATE analyses SET stage = 'COMPLETED', files_processed = files_analyzed, files_total = files_analyzed
WHERE status = 'Completed';
