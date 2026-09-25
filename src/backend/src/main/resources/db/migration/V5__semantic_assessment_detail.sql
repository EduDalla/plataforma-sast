ALTER TABLE ai_assessments ADD COLUMN risk VARCHAR(1200);
ALTER TABLE ai_assessments ADD COLUMN evidence VARCHAR(1600);
ALTER TABLE ai_assessments ADD COLUMN false_positive_reason VARCHAR(800);
ALTER TABLE ai_assessments ADD COLUMN limitations VARCHAR(800);
ALTER TABLE ai_assessments ADD COLUMN recommendations VARCHAR(1600);
