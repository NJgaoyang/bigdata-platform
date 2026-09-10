-- A workflow must have exactly one local schedule configuration. Keep the newest
-- row before applying the uniqueness constraint for installations upgraded from
-- earlier builds that allowed duplicates.
DELETE older
FROM schedule_config older
JOIN schedule_config newer
  ON older.workflow_id = newer.workflow_id
 AND older.id < newer.id;

ALTER TABLE schedule_config
    ADD CONSTRAINT uk_schedule_config_workflow UNIQUE (workflow_id);

CREATE INDEX idx_query_execution_started_at ON query_execution(started_at);
