-- A cluster version is user-provided metadata, not a value the platform can safely infer.
-- Older schema versions defaulted missing values to 3.1.9, which could present a
-- configured guess as a detected runtime version. New/edited clusters may keep it unknown.
ALTER TABLE dolphinscheduler_cluster
    MODIFY COLUMN version VARCHAR(32) NULL DEFAULT NULL;
