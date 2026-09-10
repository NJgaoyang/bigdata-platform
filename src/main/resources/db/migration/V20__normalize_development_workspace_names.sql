-- “数仓” was an implementation-only backing project created by early frontend builds.
-- It must not appear as a user folder. Keep all existing folders/files and only
-- normalize the container name so upgrades are lossless.
UPDATE dev_project
SET name = '项目空间'
WHERE name = '数仓'
  AND description = '团队公共开发空间';
