-- Data development now uses one shared project for all code.
-- Keep file/folder/version ids unchanged so release, schedule and audit references remain valid.
INSERT INTO dev_project(name, description, status, owner_name)
SELECT '生产项目', '系统唯一共享开发项目', 'ACTIVE', 'admin'
WHERE NOT EXISTS (SELECT 1 FROM dev_project);

SET @shared_project_id := (
    SELECT id FROM dev_project
    ORDER BY CASE
        WHEN name = '项目空间' THEN 0
        WHEN name = '生产项目' THEN 1
        ELSE 2
    END, id
    LIMIT 1
);

UPDATE dev_project
SET name = '生产项目', description = '系统唯一共享开发项目', status = 'ACTIVE', owner_name = 'admin'
WHERE id = @shared_project_id;

UPDATE dev_folder
SET project_id = @shared_project_id
WHERE project_id <> @shared_project_id;
UPDATE dev_file
SET project_id = @shared_project_id
WHERE project_id <> @shared_project_id;

DELETE FROM project_member
WHERE project_id <> @shared_project_id;

DELETE FROM dev_project
WHERE id <> @shared_project_id;
