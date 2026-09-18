-- 为 datasphere 元数据库全部现有表和字段补充中文注释。
-- 仅修改 COMMENT，不改变字段类型、默认值、约束或索引语义。

ALTER TABLE `alert_channel`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `name` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT '名称',
  MODIFY COLUMN `channel_type` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '渠道类型',
  MODIFY COLUMN `config_json` text COLLATE utf8mb4_general_ci NOT NULL COMMENT '配置JSON',
  MODIFY COLUMN `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  COMMENT = '告警通知渠道配置';

ALTER TABLE `alert_delivery_history`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `channel_id` bigint DEFAULT NULL COMMENT '渠道ID',
  MODIFY COLUMN `channel_name` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT '渠道名称',
  MODIFY COLUMN `channel_type` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '渠道类型',
  MODIFY COLUMN `task_name` varchar(255) COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务名称',
  MODIFY COLUMN `task_status` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务状态',
  MODIFY COLUMN `message` text COLLATE utf8mb4_general_ci COMMENT '消息内容',
  MODIFY COLUMN `delivery_status` varchar(16) COLLATE utf8mb4_general_ci NOT NULL COMMENT '投递状态',
  MODIFY COLUMN `response_message` text COLLATE utf8mb4_general_ci COMMENT '响应消息',
  MODIFY COLUMN `pushed_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '推送时间',
  COMMENT = '告警推送历史';

ALTER TABLE `asset_favorite`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `username` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户名',
  MODIFY COLUMN `asset_type` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '资产类型',
  MODIFY COLUMN `asset_ref` varchar(512) COLLATE utf8mb4_general_ci NOT NULL COMMENT '资产引用',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  COMMENT = '数据资产收藏';

ALTER TABLE `cdc_server_id_allocation`
  MODIFY COLUMN `job_id` bigint NOT NULL COMMENT '作业ID',
  MODIFY COLUMN `start_id` int NOT NULL COMMENT '起始ID',
  MODIFY COLUMN `end_id` int NOT NULL COMMENT '结束ID',
  MODIFY COLUMN `parallelism` int NOT NULL COMMENT '并行度',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  COMMENT = 'CDC 服务端ID分配记录';

ALTER TABLE `data_lineage`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `source_table_name` varchar(512) COLLATE utf8mb4_general_ci NOT NULL COMMENT '源表名称',
  MODIFY COLUMN `target_table_name` varchar(512) COLLATE utf8mb4_general_ci NOT NULL COMMENT '目标表名称',
  MODIFY COLUMN `relation_type` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '关系类型',
  MODIFY COLUMN `dev_file_id` bigint DEFAULT NULL COMMENT '开发文件ID',
  MODIFY COLUMN `file_version_id` bigint DEFAULT NULL COMMENT '文件版本ID',
  MODIFY COLUMN `workflow_id` bigint DEFAULT NULL COMMENT '工作流ID',
  MODIFY COLUMN `sql_hash` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'SQL 哈希',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  COMMENT = '数据血缘关系';

ALTER TABLE `data_source`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `name` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT '名称',
  MODIFY COLUMN `type` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '类型',
  MODIFY COLUMN `host` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '主机地址',
  MODIFY COLUMN `port` int DEFAULT NULL COMMENT '端口',
  MODIFY COLUMN `database_name` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '数据库名称',
  MODIFY COLUMN `timezone` varchar(64) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '时区',
  MODIFY COLUMN `username` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '用户名',
  MODIFY COLUMN `password_ciphertext` varchar(2000) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '加密密码',
  MODIFY COLUMN `status` varchar(32) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  MODIFY COLUMN `metadata_visible` tinyint(1) NOT NULL DEFAULT '1' COMMENT '元数据是否可见',
  MODIFY COLUMN `last_checked_at` timestamp NULL DEFAULT NULL COMMENT '最近检查时间',
  MODIFY COLUMN `last_check_message` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '最近检查信息',
  COMMENT = '数据源配置';

ALTER TABLE `dataset_definition`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `dataset_code` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT '数据集编码',
  MODIFY COLUMN `dataset_name` varchar(255) COLLATE utf8mb4_general_ci NOT NULL COMMENT '数据集名称',
  MODIFY COLUMN `description` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '描述',
  MODIFY COLUMN `source_datasource_id` bigint DEFAULT NULL COMMENT '源数据源ID',
  MODIFY COLUMN `source_database` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '源数据库',
  MODIFY COLUMN `source_table` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '源表',
  MODIFY COLUMN `owner_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '负责人',
  MODIFY COLUMN `status` varchar(32) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  COMMENT = '数据集定义';

ALTER TABLE `datasource_permission`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `datasource_id` bigint NOT NULL COMMENT '数据源ID',
  MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '用户ID',
  MODIFY COLUMN `permission_code` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT '权限编码',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  COMMENT = '数据源权限';

ALTER TABLE `dev_file`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '项目ID',
  MODIFY COLUMN `folder_id` bigint DEFAULT NULL COMMENT '目录ID',
  MODIFY COLUMN `name` varchar(255) COLLATE utf8mb4_general_ci NOT NULL COMMENT '名称',
  MODIFY COLUMN `file_type` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '文件类型',
  MODIFY COLUMN `content` text COLLATE utf8mb4_general_ci NOT NULL COMMENT '内容',
  MODIFY COLUMN `status` varchar(32) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'DRAFT' COMMENT '状态',
  MODIFY COLUMN `lifecycle_status` varchar(16) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'OFFLINE' COMMENT '生命周期状态',
  MODIFY COLUMN `ever_online` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否曾上线',
  MODIFY COLUMN `owner_name` varchar(128) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'admin' COMMENT '负责人',
  MODIFY COLUMN `current_version` int NOT NULL DEFAULT '1' COMMENT '当前版本',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  MODIFY COLUMN `description` varchar(1000) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '描述',
  MODIFY COLUMN `recycled` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否进入回收箱',
  MODIFY COLUMN `recycled_at` timestamp NULL DEFAULT NULL COMMENT '回收时间',
  MODIFY COLUMN `recycled_by` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '回收人',
  COMMENT = '数据开发任务';

ALTER TABLE `dev_file_delivery`
  MODIFY COLUMN `project_file_id` bigint NOT NULL COMMENT '项目文件ID',
  MODIFY COLUMN `source_file_id` bigint NOT NULL COMMENT '源文件ID',
  MODIFY COLUMN `pushed_version_no` int NOT NULL COMMENT '推送版本序号',
  MODIFY COLUMN `online_version_no` int DEFAULT NULL COMMENT '线上版本序号',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  COMMENT = '数据开发历史交付关系';

ALTER TABLE `dev_file_recent`
  MODIFY COLUMN `user_name` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户名',
  MODIFY COLUMN `file_id` bigint NOT NULL COMMENT '文件ID',
  MODIFY COLUMN `last_opened_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近打开时间',
  COMMENT = '数据开发最近访问记录';

ALTER TABLE `dev_file_release_bundle`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `file_id` bigint NOT NULL COMMENT '文件ID',
  MODIFY COLUMN `release_no` int NOT NULL COMMENT '发布序号',
  MODIFY COLUMN `sql_version` int NOT NULL COMMENT 'SQL版本',
  MODIFY COLUMN `schedule_version` int NOT NULL DEFAULT '0' COMMENT '调度版本',
  MODIFY COLUMN `current_flag` tinyint(1) NOT NULL DEFAULT '1' COMMENT '当前版本标记',
  MODIFY COLUMN `operator_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '操作人名称',
  MODIFY COLUMN `remark` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  MODIFY COLUMN `released_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  COMMENT = '数据开发统一发布版本';

ALTER TABLE `dev_file_schedule`
  MODIFY COLUMN `file_id` bigint NOT NULL COMMENT '文件ID',
  MODIFY COLUMN `current_version` int NOT NULL DEFAULT '0' COMMENT '当前版本',
  MODIFY COLUMN `published_version` int NOT NULL DEFAULT '0' COMMENT '已发布版本',
  MODIFY COLUMN `enabled` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否启用',
  MODIFY COLUMN `cycle_type` varchar(32) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'DAILY' COMMENT '调度周期类型',
  MODIFY COLUMN `execution_time` varchar(16) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '02:00' COMMENT '执行时间',
  MODIFY COLUMN `cron_expression` varchar(128) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0 0 2 * * ?' COMMENT 'Cron 表达式',
  MODIFY COLUMN `timezone` varchar(64) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '时区',
  MODIFY COLUMN `data_source_id` bigint DEFAULT NULL COMMENT '数据源ID',
  MODIFY COLUMN `database_name` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '数据库名称',
  MODIFY COLUMN `biz_date_param` varchar(64) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'PREVIOUS_DAY' COMMENT '业务日期参数',
  MODIFY COLUMN `retry_times` int NOT NULL DEFAULT '3' COMMENT '重试次数',
  MODIFY COLUMN `retry_interval_minutes` int NOT NULL DEFAULT '5' COMMENT '重试间隔分钟',
  MODIFY COLUMN `timeout_minutes` int NOT NULL DEFAULT '120' COMMENT '超时分钟',
  MODIFY COLUMN `updated_by` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  COMMENT = '数据开发调度配置';

ALTER TABLE `dev_file_schedule_dependency`
  MODIFY COLUMN `file_id` bigint NOT NULL COMMENT '文件ID',
  MODIFY COLUMN `upstream_file_id` bigint NOT NULL COMMENT '上游文件ID',
  COMMENT = '数据开发调度依赖';

ALTER TABLE `dev_file_schedule_execution`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `file_id` bigint NOT NULL COMMENT '文件ID',
  MODIFY COLUMN `release_no` int NOT NULL DEFAULT '0' COMMENT '发布序号',
  MODIFY COLUMN `sql_version` int NOT NULL COMMENT 'SQL版本',
  MODIFY COLUMN `schedule_version` int NOT NULL COMMENT '调度版本',
  MODIFY COLUMN `business_date` date DEFAULT NULL COMMENT '业务日期',
  MODIFY COLUMN `attempt_no` int NOT NULL DEFAULT '0' COMMENT '尝试序号',
  MODIFY COLUMN `execution_id` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '执行ID',
  MODIFY COLUMN `status` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '状态',
  MODIFY COLUMN `planned_at` timestamp NULL DEFAULT NULL COMMENT '计划时间',
  MODIFY COLUMN `started_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
  MODIFY COLUMN `finished_at` timestamp NULL DEFAULT NULL COMMENT '结束时间',
  MODIFY COLUMN `error_message` text COLLATE utf8mb4_general_ci COMMENT '错误消息',
  COMMENT = '数据开发调度执行记录';

ALTER TABLE `dev_file_schedule_version`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `file_id` bigint NOT NULL COMMENT '文件ID',
  MODIFY COLUMN `version_no` int NOT NULL COMMENT '版本序号',
  MODIFY COLUMN `config_json` text COLLATE utf8mb4_general_ci NOT NULL COMMENT '配置JSON',
  MODIFY COLUMN `created_by` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  COMMENT = '数据开发调度版本';

ALTER TABLE `dev_file_version`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `file_id` bigint NOT NULL COMMENT '文件ID',
  MODIFY COLUMN `version_no` int NOT NULL COMMENT '版本序号',
  MODIFY COLUMN `content` text COLLATE utf8mb4_general_ci NOT NULL COMMENT '内容',
  MODIFY COLUMN `checksum` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '内容校验值',
  MODIFY COLUMN `publish_flag` tinyint(1) NOT NULL DEFAULT '0' COMMENT '发布标记',
  MODIFY COLUMN `created_by` bigint DEFAULT NULL COMMENT '创建人',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  COMMENT = '数据开发代码版本';

ALTER TABLE `dev_folder`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '项目ID',
  MODIFY COLUMN `parent_id` bigint DEFAULT NULL COMMENT '上级ID',
  MODIFY COLUMN `name` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT '名称',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  COMMENT = '数据开发目录';

ALTER TABLE `dev_project`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `name` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT '名称',
  MODIFY COLUMN `description` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '描述',
  MODIFY COLUMN `status` varchar(32) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  MODIFY COLUMN `owner_name` varchar(128) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'admin' COMMENT '负责人',
  COMMENT = '数据开发共享项目';

ALTER TABLE `dolphinscheduler_cluster`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `name` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT '名称',
  MODIFY COLUMN `host` varchar(255) COLLATE utf8mb4_general_ci NOT NULL COMMENT '主机地址',
  MODIFY COLUMN `port` int NOT NULL DEFAULT '12345' COMMENT '端口',
  MODIFY COLUMN `base_path` varchar(255) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '/dolphinscheduler' COMMENT '服务基础路径',
  MODIFY COLUMN `version` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '版本',
  MODIFY COLUMN `username` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '用户名',
  MODIFY COLUMN `password_encrypted` text COLLATE utf8mb4_general_ci COMMENT '加密密码',
  MODIFY COLUMN `install_dir` varchar(512) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '安装目录',
  MODIFY COLUMN `description` varchar(512) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '描述',
  MODIFY COLUMN `health_status` varchar(32) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'UNKNOWN' COMMENT '健康状态',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  COMMENT = 'DolphinScheduler 集群配置';

ALTER TABLE `flink_environment`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `name` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT '名称',
  MODIFY COLUMN `engine_type` varchar(32) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'FLINK_CDC' COMMENT '引擎类型',
  MODIFY COLUMN `deployment_mode` varchar(32) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'REMOTE' COMMENT '部署模式',
  MODIFY COLUMN `submitter_type` varchar(16) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'LOCAL' COMMENT '提交者类型',
  MODIFY COLUMN `rest_url` varchar(512) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'REST 服务地址',
  MODIFY COLUMN `flink_home` varchar(512) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'Flink 安装目录',
  MODIFY COLUMN `flink_cdc_home` varchar(512) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'Flink CDC 安装目录',
  MODIFY COLUMN `java_home` varchar(512) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'Java 安装目录',
  MODIFY COLUMN `flink_version` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'Flink 版本',
  MODIFY COLUMN `flink_cdc_version` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'Flink CDC 版本',
  MODIFY COLUMN `ssh_host` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'SSH 主机',
  MODIFY COLUMN `ssh_port` int DEFAULT '22' COMMENT 'SSH 端口',
  MODIFY COLUMN `ssh_username` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'SSH 用户名',
  MODIFY COLUMN `ssh_password_ciphertext` varchar(2000) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'SSH 加密密码',
  MODIFY COLUMN `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用',
  MODIFY COLUMN `default_environment` tinyint(1) NOT NULL DEFAULT '0' COMMENT '默认运行环境',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  COMMENT = 'Flink 运行环境配置';

ALTER TABLE `flyway_schema_history`
  MODIFY COLUMN `installed_rank` int NOT NULL COMMENT '安装序号',
  MODIFY COLUMN `version` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '版本',
  MODIFY COLUMN `description` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '描述',
  MODIFY COLUMN `type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '类型',
  MODIFY COLUMN `script` varchar(1000) COLLATE utf8mb4_general_ci NOT NULL COMMENT '脚本内容',
  MODIFY COLUMN `checksum` int DEFAULT NULL COMMENT '内容校验值',
  MODIFY COLUMN `installed_by` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '安装人',
  MODIFY COLUMN `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '安装时间',
  MODIFY COLUMN `execution_time` int NOT NULL COMMENT '执行时间',
  MODIFY COLUMN `success` tinyint(1) NOT NULL COMMENT '是否成功',
  COMMENT = 'Flyway 数据库迁移历史';

ALTER TABLE `integration_attempt`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `batch_id` bigint NOT NULL COMMENT '批次ID',
  MODIFY COLUMN `attempt_no` int NOT NULL COMMENT '尝试序号',
  MODIFY COLUMN `execution_id` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '执行ID',
  MODIFY COLUMN `status` varchar(32) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'SUBMITTED' COMMENT '状态',
  MODIFY COLUMN `started_at` timestamp NULL DEFAULT NULL COMMENT '开始时间',
  MODIFY COLUMN `finished_at` timestamp NULL DEFAULT NULL COMMENT '结束时间',
  MODIFY COLUMN `error_message` text COLLATE utf8mb4_general_ci COMMENT '错误消息',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  COMMENT = '离线集成执行尝试';

ALTER TABLE `integration_batch`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `task_id` bigint NOT NULL COMMENT '任务ID',
  MODIFY COLUMN `batch_code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '批次编码',
  MODIFY COLUMN `trigger_type` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发类型',
  MODIFY COLUMN `status` varchar(32) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'QUEUED' COMMENT '状态',
  MODIFY COLUMN `runtime_config_encrypted` longtext COLLATE utf8mb4_general_ci NOT NULL COMMENT '运行时配置密文',
  MODIFY COLUMN `cluster_id` bigint DEFAULT NULL COMMENT '集群ID',
  MODIFY COLUMN `parameters_json` text COLLATE utf8mb4_general_ci COMMENT '参数JSON',
  MODIFY COLUMN `source_batch_id` bigint DEFAULT NULL COMMENT '源批次ID',
  MODIFY COLUMN `created_by` varchar(128) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'platform' COMMENT '创建人',
  MODIFY COLUMN `started_at` timestamp NULL DEFAULT NULL COMMENT '开始时间',
  MODIFY COLUMN `finished_at` timestamp NULL DEFAULT NULL COMMENT '结束时间',
  MODIFY COLUMN `error_message` text COLLATE utf8mb4_general_ci COMMENT '错误消息',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  COMMENT = '离线集成执行批次';

ALTER TABLE `integration_cursor`
  MODIFY COLUMN `task_id` bigint NOT NULL COMMENT '任务ID',
  MODIFY COLUMN `cursor_column` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '游标字段',
  MODIFY COLUMN `cursor_value` varchar(512) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '游标值',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  COMMENT = '离线增量同步游标';

ALTER TABLE `integration_instance`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `task_id` bigint NOT NULL COMMENT '任务ID',
  MODIFY COLUMN `execution_id` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '执行ID',
  MODIFY COLUMN `ds_process_instance_code` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'DolphinScheduler 流程实例编码',
  MODIFY COLUMN `status` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '状态',
  MODIFY COLUMN `started_at` timestamp NULL DEFAULT NULL COMMENT '开始时间',
  MODIFY COLUMN `finished_at` timestamp NULL DEFAULT NULL COMMENT '结束时间',
  MODIFY COLUMN `elapsed_ms` bigint DEFAULT NULL COMMENT '耗时毫秒',
  MODIFY COLUMN `error_message` text COLLATE utf8mb4_general_ci COMMENT '错误消息',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  COMMENT = '离线集成执行实例';

ALTER TABLE `integration_task`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `name` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT '名称',
  MODIFY COLUMN `source_type` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '源类型',
  MODIFY COLUMN `target_type` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '目标类型',
  MODIFY COLUMN `source_config_json` text COLLATE utf8mb4_general_ci NOT NULL COMMENT '源配置JSON',
  MODIFY COLUMN `target_config_json` text COLLATE utf8mb4_general_ci NOT NULL COMMENT '目标配置JSON',
  MODIFY COLUMN `transform_config_json` text COLLATE utf8mb4_general_ci COMMENT '转换配置JSON',
  MODIFY COLUMN `seatunnel_config` text COLLATE utf8mb4_general_ci COMMENT 'SeaTunnel 配置',
  MODIFY COLUMN `sync_mode` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '同步模式',
  MODIFY COLUMN `status` varchar(32) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'DRAFT' COMMENT '状态',
  MODIFY COLUMN `lifecycle_status` varchar(16) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'ONLINE' COMMENT '生命周期状态',
  MODIFY COLUMN `created_by` varchar(128) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'platform' COMMENT '创建人',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  COMMENT = '离线集成任务';

ALTER TABLE `integration_task_schedule`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `task_id` bigint NOT NULL COMMENT '任务ID',
  MODIFY COLUMN `cron_expression` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'Cron 表达式',
  MODIFY COLUMN `timezone` varchar(64) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '时区',
  MODIFY COLUMN `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  COMMENT = '离线集成调度配置';

ALTER TABLE `integration_task_table`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `task_id` bigint NOT NULL COMMENT '任务ID',
  MODIFY COLUMN `source_database` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT '源数据库',
  MODIFY COLUMN `source_table` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT '源表',
  MODIFY COLUMN `target_database` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT '目标数据库',
  MODIFY COLUMN `target_table` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT '目标表',
  MODIFY COLUMN `partition_column` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '分区字段',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  COMMENT = '离线集成表映射';

ALTER TABLE `integration_validation_result`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `batch_id` bigint DEFAULT NULL COMMENT '批次ID',
  MODIFY COLUMN `task_id` bigint NOT NULL COMMENT '任务ID',
  MODIFY COLUMN `execution_id` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '执行ID',
  MODIFY COLUMN `source_database` varchar(255) COLLATE utf8mb4_general_ci NOT NULL COMMENT '源数据库',
  MODIFY COLUMN `source_table` varchar(255) COLLATE utf8mb4_general_ci NOT NULL COMMENT '源表',
  MODIFY COLUMN `target_database` varchar(255) COLLATE utf8mb4_general_ci NOT NULL COMMENT '目标数据库',
  MODIFY COLUMN `target_table` varchar(255) COLLATE utf8mb4_general_ci NOT NULL COMMENT '目标表',
  MODIFY COLUMN `source_count` bigint DEFAULT NULL COMMENT '源数量',
  MODIFY COLUMN `target_count` bigint DEFAULT NULL COMMENT '目标数量',
  MODIFY COLUMN `status` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '状态',
  MODIFY COLUMN `detail` varchar(2000) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '详情',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  COMMENT = '离线集成校验结果';

ALTER TABLE `metadata_table_owner`
  MODIFY COLUMN `data_source_id` bigint NOT NULL COMMENT '数据源ID',
  MODIFY COLUMN `database_name` varchar(255) COLLATE utf8mb4_general_ci NOT NULL COMMENT '数据库名称',
  MODIFY COLUMN `table_name` varchar(255) COLLATE utf8mb4_general_ci NOT NULL COMMENT '表名称',
  MODIFY COLUMN `owner_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '负责人',
  MODIFY COLUMN `updated_by` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  COMMENT = '元数据表负责人';

ALTER TABLE `metric_definition`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `metric_code` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT '指标编码',
  MODIFY COLUMN `metric_name` varchar(255) COLLATE utf8mb4_general_ci NOT NULL COMMENT '指标名称',
  MODIFY COLUMN `metric_type` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '指标类型',
  MODIFY COLUMN `description` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '描述',
  MODIFY COLUMN `business_domain` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '业务域',
  MODIFY COLUMN `owner_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '负责人',
  MODIFY COLUMN `status` varchar(32) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'DRAFT' COMMENT '状态',
  MODIFY COLUMN `current_version` int NOT NULL DEFAULT '1' COMMENT '当前版本',
  MODIFY COLUMN `source_datasource_id` bigint DEFAULT NULL COMMENT '源数据源ID',
  MODIFY COLUMN `source_database` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '源数据库',
  MODIFY COLUMN `source_table` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '源表',
  MODIFY COLUMN `source_field` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '源字段',
  MODIFY COLUMN `aggregation` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '聚合方式',
  MODIFY COLUMN `filter_expression` text COLLATE utf8mb4_general_ci COMMENT '过滤表达式',
  MODIFY COLUMN `time_field` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '时间字段',
  MODIFY COLUMN `expression_text` text COLLATE utf8mb4_general_ci COMMENT '表达式',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  COMMENT = '指标定义';

ALTER TABLE `metric_dimension`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `dimension_code` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT '维度编码',
  MODIFY COLUMN `dimension_name` varchar(255) COLLATE utf8mb4_general_ci NOT NULL COMMENT '维度名称',
  MODIFY COLUMN `description` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '描述',
  MODIFY COLUMN `source_datasource_id` bigint DEFAULT NULL COMMENT '源数据源ID',
  MODIFY COLUMN `source_database` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '源数据库',
  MODIFY COLUMN `source_table` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '源表',
  MODIFY COLUMN `source_field` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '源字段',
  MODIFY COLUMN `owner_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '负责人',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  COMMENT = '指标维度';

ALTER TABLE `metric_dimension_relation`
  MODIFY COLUMN `metric_id` bigint NOT NULL COMMENT '指标ID',
  MODIFY COLUMN `dimension_id` bigint NOT NULL COMMENT '维度ID',
  COMMENT = '指标维度关联';

ALTER TABLE `metric_lineage`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `metric_id` bigint NOT NULL COMMENT '指标ID',
  MODIFY COLUMN `upstream_type` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '上游类型',
  MODIFY COLUMN `upstream_ref` varchar(512) COLLATE utf8mb4_general_ci NOT NULL COMMENT '上游引用',
  MODIFY COLUMN `downstream_type` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '下游类型',
  MODIFY COLUMN `downstream_ref` varchar(512) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '下游引用',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  COMMENT = '指标血缘关系';

ALTER TABLE `metric_version`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `metric_id` bigint NOT NULL COMMENT '指标ID',
  MODIFY COLUMN `version_no` int NOT NULL COMMENT '版本序号',
  MODIFY COLUMN `definition_json` longtext COLLATE utf8mb4_general_ci NOT NULL COMMENT '定义JSON',
  MODIFY COLUMN `created_by` varchar(128) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'admin' COMMENT '创建人',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  COMMENT = '指标版本';

ALTER TABLE `operation_audit`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `action` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT '操作动作',
  MODIFY COLUMN `resource_type` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '资源类型',
  MODIFY COLUMN `resource_id` bigint DEFAULT NULL COMMENT '资源ID',
  MODIFY COLUMN `detail` text COLLATE utf8mb4_general_ci COMMENT '详情',
  MODIFY COLUMN `operator_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '操作人名称',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  COMMENT = '操作审计日志';

ALTER TABLE `operation_log`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `action` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT '操作动作',
  MODIFY COLUMN `resource_type` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '资源类型',
  MODIFY COLUMN `resource_id` bigint DEFAULT NULL COMMENT '资源ID',
  MODIFY COLUMN `detail` text COLLATE utf8mb4_general_ci COMMENT '详情',
  MODIFY COLUMN `operator_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '操作人名称',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  COMMENT = '运维操作日志';

ALTER TABLE `platform_role`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `role_code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色编码',
  MODIFY COLUMN `role_name` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色名称',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  COMMENT = '平台角色';

ALTER TABLE `platform_user`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `username` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户名',
  MODIFY COLUMN `display_name` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT '显示名称',
  MODIFY COLUMN `phone` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '手机号',
  MODIFY COLUMN `status` varchar(32) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `password_hash` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '密码哈希',
  MODIFY COLUMN `role_code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'USER' COMMENT '角色编码',
  COMMENT = '平台用户';

ALTER TABLE `project_member`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '项目ID',
  MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '用户ID',
  MODIFY COLUMN `permission_code` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT '权限编码',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  COMMENT = '历史项目成员权限';

ALTER TABLE `qrtz_blob_triggers`
  MODIFY COLUMN `SCHED_NAME` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度器名称',
  MODIFY COLUMN `TRIGGER_NAME` varchar(190) COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发器名称',
  MODIFY COLUMN `TRIGGER_GROUP` varchar(190) COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发器组',
  MODIFY COLUMN `BLOB_DATA` blob COMMENT '二进制数据',
  COMMENT = 'Quartz 二进制触发器';

ALTER TABLE `qrtz_calendars`
  MODIFY COLUMN `SCHED_NAME` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度器名称',
  MODIFY COLUMN `CALENDAR_NAME` varchar(190) COLLATE utf8mb4_general_ci NOT NULL COMMENT '日历名称',
  MODIFY COLUMN `CALENDAR` blob NOT NULL COMMENT '日历数据',
  COMMENT = 'Quartz 日历';

ALTER TABLE `qrtz_cron_triggers`
  MODIFY COLUMN `SCHED_NAME` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度器名称',
  MODIFY COLUMN `TRIGGER_NAME` varchar(190) COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发器名称',
  MODIFY COLUMN `TRIGGER_GROUP` varchar(190) COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发器组',
  MODIFY COLUMN `CRON_EXPRESSION` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'Cron 表达式',
  MODIFY COLUMN `TIME_ZONE_ID` varchar(80) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '时区ID',
  COMMENT = 'Quartz Cron 触发器';

ALTER TABLE `qrtz_fired_triggers`
  MODIFY COLUMN `SCHED_NAME` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度器名称',
  MODIFY COLUMN `ENTRY_ID` varchar(95) COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发实例ID',
  MODIFY COLUMN `TRIGGER_NAME` varchar(190) COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发器名称',
  MODIFY COLUMN `TRIGGER_GROUP` varchar(190) COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发器组',
  MODIFY COLUMN `INSTANCE_NAME` varchar(190) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度实例名称',
  MODIFY COLUMN `FIRED_TIME` bigint NOT NULL COMMENT '实际触发时间',
  MODIFY COLUMN `SCHED_TIME` bigint NOT NULL COMMENT '计划触发时间',
  MODIFY COLUMN `PRIORITY` int NOT NULL COMMENT '优先级',
  MODIFY COLUMN `STATE` varchar(16) COLLATE utf8mb4_general_ci NOT NULL COMMENT '状态',
  MODIFY COLUMN `JOB_NAME` varchar(190) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '作业名称',
  MODIFY COLUMN `JOB_GROUP` varchar(190) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '作业组',
  MODIFY COLUMN `IS_NONCONCURRENT` varchar(1) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '是否禁止并发执行',
  MODIFY COLUMN `REQUESTS_RECOVERY` varchar(1) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '是否请求恢复',
  COMMENT = 'Quartz 已触发实例';

ALTER TABLE `qrtz_job_details`
  MODIFY COLUMN `SCHED_NAME` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度器名称',
  MODIFY COLUMN `JOB_NAME` varchar(190) COLLATE utf8mb4_general_ci NOT NULL COMMENT '作业名称',
  MODIFY COLUMN `JOB_GROUP` varchar(190) COLLATE utf8mb4_general_ci NOT NULL COMMENT '作业组',
  MODIFY COLUMN `DESCRIPTION` varchar(250) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '描述',
  MODIFY COLUMN `JOB_CLASS_NAME` varchar(250) COLLATE utf8mb4_general_ci NOT NULL COMMENT '作业实现类',
  MODIFY COLUMN `IS_DURABLE` varchar(1) COLLATE utf8mb4_general_ci NOT NULL COMMENT '是否持久化作业',
  MODIFY COLUMN `IS_NONCONCURRENT` varchar(1) COLLATE utf8mb4_general_ci NOT NULL COMMENT '是否禁止并发执行',
  MODIFY COLUMN `IS_UPDATE_DATA` varchar(1) COLLATE utf8mb4_general_ci NOT NULL COMMENT '执行后是否更新数据',
  MODIFY COLUMN `REQUESTS_RECOVERY` varchar(1) COLLATE utf8mb4_general_ci NOT NULL COMMENT '是否请求恢复',
  MODIFY COLUMN `JOB_DATA` blob COMMENT '作业数据',
  COMMENT = 'Quartz 作业详情';

ALTER TABLE `qrtz_locks`
  MODIFY COLUMN `SCHED_NAME` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度器名称',
  MODIFY COLUMN `LOCK_NAME` varchar(40) COLLATE utf8mb4_general_ci NOT NULL COMMENT '锁名称',
  COMMENT = 'Quartz 集群锁';

ALTER TABLE `qrtz_paused_trigger_grps`
  MODIFY COLUMN `SCHED_NAME` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度器名称',
  MODIFY COLUMN `TRIGGER_GROUP` varchar(190) COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发器组',
  COMMENT = 'Quartz 暂停触发器组';

ALTER TABLE `qrtz_scheduler_state`
  MODIFY COLUMN `SCHED_NAME` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度器名称',
  MODIFY COLUMN `INSTANCE_NAME` varchar(190) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度实例名称',
  MODIFY COLUMN `LAST_CHECKIN_TIME` bigint NOT NULL COMMENT '最后心跳时间',
  MODIFY COLUMN `CHECKIN_INTERVAL` bigint NOT NULL COMMENT '心跳间隔',
  COMMENT = 'Quartz 调度器状态';

ALTER TABLE `qrtz_simple_triggers`
  MODIFY COLUMN `SCHED_NAME` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度器名称',
  MODIFY COLUMN `TRIGGER_NAME` varchar(190) COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发器名称',
  MODIFY COLUMN `TRIGGER_GROUP` varchar(190) COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发器组',
  MODIFY COLUMN `REPEAT_COUNT` bigint NOT NULL COMMENT '重复次数',
  MODIFY COLUMN `REPEAT_INTERVAL` bigint NOT NULL COMMENT '重复间隔',
  MODIFY COLUMN `TIMES_TRIGGERED` bigint NOT NULL COMMENT '已触发次数',
  COMMENT = 'Quartz 简单触发器';

ALTER TABLE `qrtz_simprop_triggers`
  MODIFY COLUMN `SCHED_NAME` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度器名称',
  MODIFY COLUMN `TRIGGER_NAME` varchar(190) COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发器名称',
  MODIFY COLUMN `TRIGGER_GROUP` varchar(190) COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发器组',
  MODIFY COLUMN `STR_PROP_1` varchar(512) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '字符串属性1',
  MODIFY COLUMN `STR_PROP_2` varchar(512) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '字符串属性2',
  MODIFY COLUMN `STR_PROP_3` varchar(512) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '字符串属性3',
  MODIFY COLUMN `INT_PROP_1` int DEFAULT NULL COMMENT '整数属性1',
  MODIFY COLUMN `INT_PROP_2` int DEFAULT NULL COMMENT '整数属性2',
  MODIFY COLUMN `LONG_PROP_1` bigint DEFAULT NULL COMMENT '长整数属性1',
  MODIFY COLUMN `LONG_PROP_2` bigint DEFAULT NULL COMMENT '长整数属性2',
  MODIFY COLUMN `DEC_PROP_1` decimal(13,4) DEFAULT NULL COMMENT '小数属性1',
  MODIFY COLUMN `DEC_PROP_2` decimal(13,4) DEFAULT NULL COMMENT '小数属性2',
  MODIFY COLUMN `BOOL_PROP_1` varchar(1) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '布尔属性1',
  MODIFY COLUMN `BOOL_PROP_2` varchar(1) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '布尔属性2',
  COMMENT = 'Quartz 属性触发器';

ALTER TABLE `qrtz_triggers`
  MODIFY COLUMN `SCHED_NAME` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度器名称',
  MODIFY COLUMN `TRIGGER_NAME` varchar(190) COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发器名称',
  MODIFY COLUMN `TRIGGER_GROUP` varchar(190) COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发器组',
  MODIFY COLUMN `JOB_NAME` varchar(190) COLLATE utf8mb4_general_ci NOT NULL COMMENT '作业名称',
  MODIFY COLUMN `JOB_GROUP` varchar(190) COLLATE utf8mb4_general_ci NOT NULL COMMENT '作业组',
  MODIFY COLUMN `DESCRIPTION` varchar(250) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '描述',
  MODIFY COLUMN `NEXT_FIRE_TIME` bigint DEFAULT NULL COMMENT '下次触发时间',
  MODIFY COLUMN `PREV_FIRE_TIME` bigint DEFAULT NULL COMMENT '上次触发时间',
  MODIFY COLUMN `PRIORITY` int DEFAULT NULL COMMENT '优先级',
  MODIFY COLUMN `TRIGGER_STATE` varchar(16) COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发器状态',
  MODIFY COLUMN `TRIGGER_TYPE` varchar(8) COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发器类型',
  MODIFY COLUMN `START_TIME` bigint NOT NULL COMMENT '开始时间',
  MODIFY COLUMN `END_TIME` bigint DEFAULT NULL COMMENT '结束时间',
  MODIFY COLUMN `CALENDAR_NAME` varchar(190) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '日历名称',
  MODIFY COLUMN `MISFIRE_INSTR` smallint DEFAULT NULL COMMENT '错过触发处理策略',
  MODIFY COLUMN `JOB_DATA` blob COMMENT '作业数据',
  COMMENT = 'Quartz 触发器';

ALTER TABLE `query_execution`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `query_id` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '查询ID',
  MODIFY COLUMN `datasource_id` bigint DEFAULT NULL COMMENT '数据源ID',
  MODIFY COLUMN `database_name` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '数据库名称',
  MODIFY COLUMN `sql_text` text COLLATE utf8mb4_general_ci NOT NULL COMMENT 'SQL 文本',
  MODIFY COLUMN `status` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '状态',
  MODIFY COLUMN `started_at` timestamp NULL DEFAULT NULL COMMENT '开始时间',
  MODIFY COLUMN `finished_at` timestamp NULL DEFAULT NULL COMMENT '结束时间',
  MODIFY COLUMN `elapsed_ms` bigint DEFAULT NULL COMMENT '耗时毫秒',
  MODIFY COLUMN `error_message` text COLLATE utf8mb4_general_ci COMMENT '错误消息',
  MODIFY COLUMN `operator_name` varchar(128) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'admin' COMMENT '操作人名称',
  COMMENT = 'SQL 查询执行记录';

ALTER TABLE `realtime_checkpoint`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `job_id` bigint NOT NULL COMMENT '作业ID',
  MODIFY COLUMN `execution_id` bigint DEFAULT NULL COMMENT '执行ID',
  MODIFY COLUMN `checkpoint_id` bigint DEFAULT NULL COMMENT 'Checkpoint ID',
  MODIFY COLUMN `status` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '状态',
  MODIFY COLUMN `duration_ms` bigint DEFAULT NULL COMMENT '耗时毫秒',
  MODIFY COLUMN `state_size_bytes` bigint DEFAULT NULL COMMENT '状态大小字节',
  MODIFY COLUMN `completed_at` timestamp NULL DEFAULT NULL COMMENT '完成时间',
  MODIFY COLUMN `raw_json` longtext COLLATE utf8mb4_general_ci COMMENT '原始数据JSON',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  COMMENT = '实时同步 Checkpoint 记录';

ALTER TABLE `realtime_schema_change`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `job_id` bigint NOT NULL COMMENT '作业ID',
  MODIFY COLUMN `execution_id` bigint DEFAULT NULL COMMENT '执行ID',
  MODIFY COLUMN `source_table` varchar(255) COLLATE utf8mb4_general_ci NOT NULL COMMENT '源表',
  MODIFY COLUMN `change_type` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '变更类型',
  MODIFY COLUMN `ddl_text` text COLLATE utf8mb4_general_ci COMMENT 'DDL 文本',
  MODIFY COLUMN `policy_action` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '策略动作',
  MODIFY COLUMN `target_result` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '目标结果',
  MODIFY COLUMN `status` varchar(32) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'DETECTED' COMMENT '状态',
  MODIFY COLUMN `detail` text COLLATE utf8mb4_general_ci COMMENT '详情',
  MODIFY COLUMN `occurred_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
  COMMENT = '实时同步 Schema 变更记录';

ALTER TABLE `realtime_schema_snapshot`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `job_id` bigint NOT NULL COMMENT '作业ID',
  MODIFY COLUMN `source_table` varchar(255) COLLATE utf8mb4_general_ci NOT NULL COMMENT '源表',
  MODIFY COLUMN `schema_json` longtext COLLATE utf8mb4_general_ci NOT NULL COMMENT 'Schema JSON',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  COMMENT = '实时同步 Schema 快照';

ALTER TABLE `realtime_sync_definition`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `name` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT '名称',
  MODIFY COLUMN `description` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '描述',
  MODIFY COLUMN `runtime_environment_id` bigint DEFAULT NULL COMMENT '运行时运行环境ID',
  MODIFY COLUMN `release_state` varchar(32) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'DRAFT' COMMENT '发布状态',
  MODIFY COLUMN `desired_state` varchar(32) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'STOPPED' COMMENT '期望状态',
  MODIFY COLUMN `observed_state` varchar(32) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'STOPPED' COMMENT '观测状态',
  MODIFY COLUMN `definition_version` int NOT NULL DEFAULT '1' COMMENT '定义版本',
  MODIFY COLUMN `published_version` int DEFAULT NULL COMMENT '已发布版本',
  MODIFY COLUMN `spec_json` longtext COLLATE utf8mb4_general_ci NOT NULL COMMENT '规格JSON',
  MODIFY COLUMN `config_digest` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '配置摘要',
  MODIFY COLUMN `last_error` text COLLATE utf8mb4_general_ci COMMENT '最近错误',
  MODIFY COLUMN `created_by` varchar(128) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'admin' COMMENT '创建人',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  COMMENT = '实时同步任务定义';

ALTER TABLE `realtime_sync_event`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `job_id` bigint NOT NULL COMMENT '作业ID',
  MODIFY COLUMN `execution_id` bigint DEFAULT NULL COMMENT '执行ID',
  MODIFY COLUMN `event_type` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '事件类型',
  MODIFY COLUMN `detail` text COLLATE utf8mb4_general_ci COMMENT '详情',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  COMMENT = '实时同步事件';

ALTER TABLE `realtime_sync_execution`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `job_id` bigint NOT NULL COMMENT '作业ID',
  MODIFY COLUMN `definition_version` int NOT NULL COMMENT '定义版本',
  MODIFY COLUMN `engine_job_id` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '引擎作业ID',
  MODIFY COLUMN `runtime_revision` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '运行时修订号',
  MODIFY COLUMN `runtime_environment_snapshot` text COLLATE utf8mb4_general_ci COMMENT '运行时运行环境快照',
  MODIFY COLUMN `status` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '状态',
  MODIFY COLUMN `result_uncertain` tinyint(1) NOT NULL DEFAULT '0' COMMENT '结果是否不确定',
  MODIFY COLUMN `error_message` text COLLATE utf8mb4_general_ci COMMENT '错误消息',
  MODIFY COLUMN `local_log` longtext COLLATE utf8mb4_general_ci COMMENT '本地日志',
  MODIFY COLUMN `started_at` timestamp NULL DEFAULT NULL COMMENT '开始时间',
  MODIFY COLUMN `finished_at` timestamp NULL DEFAULT NULL COMMENT '结束时间',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  COMMENT = '实时同步执行记录';

ALTER TABLE `realtime_sync_version`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `job_id` bigint NOT NULL COMMENT '作业ID',
  MODIFY COLUMN `version_no` int NOT NULL COMMENT '版本序号',
  MODIFY COLUMN `spec_json` longtext COLLATE utf8mb4_general_ci NOT NULL COMMENT '规格JSON',
  MODIFY COLUMN `config_digest` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '配置摘要',
  MODIFY COLUMN `published` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否已发布',
  MODIFY COLUMN `created_by` varchar(128) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'admin' COMMENT '创建人',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  COMMENT = '实时同步版本';

ALTER TABLE `realtime_validation_result`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `job_id` bigint NOT NULL COMMENT '作业ID',
  MODIFY COLUMN `execution_id` bigint DEFAULT NULL COMMENT '执行ID',
  MODIFY COLUMN `validation_type` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '校验类型',
  MODIFY COLUMN `source_table` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '源表',
  MODIFY COLUMN `source_value` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '源值',
  MODIFY COLUMN `target_value` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '目标值',
  MODIFY COLUMN `status` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '状态',
  MODIFY COLUMN `detail` text COLLATE utf8mb4_general_ci COMMENT '详情',
  MODIFY COLUMN `checked_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '检查时间',
  COMMENT = '实时同步校验结果';

ALTER TABLE `release_policy`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `policy_key` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '策略键',
  MODIFY COLUMN `approval_required` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否需要审批',
  MODIFY COLUMN `updated_by` varchar(128) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'admin' COMMENT '更新人',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  COMMENT = '发布策略';

ALTER TABLE `release_record`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `request_id` bigint DEFAULT NULL COMMENT '申请ID',
  MODIFY COLUMN `resource_type` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '资源类型',
  MODIFY COLUMN `resource_id` bigint NOT NULL COMMENT '资源ID',
  MODIFY COLUMN `resource_name` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '资源名称',
  MODIFY COLUMN `released_version` int DEFAULT NULL COMMENT '发布版本',
  MODIFY COLUMN `result_status` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '结果状态',
  MODIFY COLUMN `detail` text COLLATE utf8mb4_general_ci COMMENT '详情',
  MODIFY COLUMN `operator_name` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT '操作人名称',
  MODIFY COLUMN `released_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  COMMENT = '发布记录';

ALTER TABLE `release_request`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `resource_type` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '资源类型',
  MODIFY COLUMN `resource_id` bigint NOT NULL COMMENT '资源ID',
  MODIFY COLUMN `resource_name` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '资源名称',
  MODIFY COLUMN `requested_version` int DEFAULT NULL COMMENT '申请版本',
  MODIFY COLUMN `payload_json` longtext COLLATE utf8mb4_general_ci COMMENT '载荷JSON',
  MODIFY COLUMN `status` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '状态',
  MODIFY COLUMN `requested_by` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT '申请人',
  MODIFY COLUMN `reviewed_by` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '审核人',
  MODIFY COLUMN `review_comment` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '审核意见',
  MODIFY COLUMN `requested_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
  MODIFY COLUMN `reviewed_at` timestamp NULL DEFAULT NULL COMMENT '审核时间',
  COMMENT = '发布申请';

ALTER TABLE `role_permission`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `role_id` bigint NOT NULL COMMENT '角色ID',
  MODIFY COLUMN `permission_code` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT '权限编码',
  COMMENT = '角色权限';

ALTER TABLE `schedule_config`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `workflow_id` bigint NOT NULL COMMENT '工作流ID',
  MODIFY COLUMN `cron_expression` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'Cron 表达式',
  MODIFY COLUMN `timezone` varchar(64) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '时区',
  MODIFY COLUMN `enabled` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否启用',
  MODIFY COLUMN `failure_strategy` varchar(32) COLLATE utf8mb4_general_ci DEFAULT 'END' COMMENT '失败策略',
  MODIFY COLUMN `worker_group` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '工作组',
  MODIFY COLUMN `alert_group` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '告警组',
  MODIFY COLUMN `ds_schedule_id` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'DolphinScheduler 调度ID',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  MODIFY COLUMN `parallelism` int NOT NULL DEFAULT '1' COMMENT '并行度',
  COMMENT = '通用调度配置';

ALTER TABLE `scheduler_trigger`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `trigger_code` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发编码',
  MODIFY COLUMN `workflow_code` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT '工作流编码',
  MODIFY COLUMN `cron_expression` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'Cron 表达式',
  MODIFY COLUMN `timezone` varchar(64) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '时区',
  MODIFY COLUMN `enabled` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否启用',
  MODIFY COLUMN `failure_strategy` varchar(32) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'END' COMMENT '失败策略',
  MODIFY COLUMN `parallelism` int NOT NULL DEFAULT '1' COMMENT '并行度',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  COMMENT = '调度触发记录';

ALTER TABLE `seatunnel_cluster`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `name` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT '名称',
  MODIFY COLUMN `host` varchar(255) COLLATE utf8mb4_general_ci NOT NULL COMMENT '主机地址',
  MODIFY COLUMN `port` int NOT NULL DEFAULT '5801' COMMENT '端口',
  MODIFY COLUMN `ssh_username` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'SSH 用户名',
  MODIFY COLUMN `ssh_port` int NOT NULL DEFAULT '22' COMMENT 'SSH 端口',
  MODIFY COLUMN `ssh_password_encrypted` text COLLATE utf8mb4_general_ci COMMENT 'SSH 加密密码',
  MODIFY COLUMN `seatunnel_home` varchar(512) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'SeaTunnel 安装目录',
  MODIFY COLUMN `description` varchar(512) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '描述',
  MODIFY COLUMN `health_status` varchar(32) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'UNKNOWN' COMMENT '健康状态',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  COMMENT = 'SeaTunnel 集群配置';

ALTER TABLE `task_instance`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `workflow_instance_id` bigint NOT NULL COMMENT '工作流实例ID',
  MODIFY COLUMN `node_id` bigint NOT NULL COMMENT '节点ID',
  MODIFY COLUMN `node_name` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT '节点名称',
  MODIFY COLUMN `node_type` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '节点类型',
  MODIFY COLUMN `attempt_no` int NOT NULL DEFAULT '1' COMMENT '尝试序号',
  MODIFY COLUMN `status` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '状态',
  MODIFY COLUMN `started_at` timestamp NULL DEFAULT NULL COMMENT '开始时间',
  MODIFY COLUMN `finished_at` timestamp NULL DEFAULT NULL COMMENT '结束时间',
  MODIFY COLUMN `error_message` text COLLATE utf8mb4_general_ci COMMENT '错误消息',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  COMMENT = '任务实例';

ALTER TABLE `task_log`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `task_instance_id` bigint NOT NULL COMMENT '任务实例ID',
  MODIFY COLUMN `log_level` varchar(16) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'INFO' COMMENT '日志级别',
  MODIFY COLUMN `message` longtext COLLATE utf8mb4_general_ci NOT NULL COMMENT '消息内容',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  COMMENT = '任务日志';

ALTER TABLE `user_permission`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '用户ID',
  MODIFY COLUMN `permission_code` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT '权限编码',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  COMMENT = '用户权限';

ALTER TABLE `workflow`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `name` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT '名称',
  MODIFY COLUMN `workflow_code` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT '工作流编码',
  MODIFY COLUMN `description` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '描述',
  MODIFY COLUMN `status` varchar(32) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'DRAFT' COMMENT '状态',
  MODIFY COLUMN `ds_process_code` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'DolphinScheduler 流程编码',
  MODIFY COLUMN `published_version` int DEFAULT NULL COMMENT '已发布版本',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  COMMENT = '工作流';

ALTER TABLE `workflow_definition_version`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `workflow_code` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT '工作流编码',
  MODIFY COLUMN `workflow_name` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT '工作流名称',
  MODIFY COLUMN `version_no` int NOT NULL COMMENT '版本序号',
  MODIFY COLUMN `definition_json` longtext COLLATE utf8mb4_general_ci NOT NULL COMMENT '定义JSON',
  MODIFY COLUMN `status` varchar(32) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'PUBLISHED' COMMENT '状态',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  COMMENT = '工作流定义版本';

ALTER TABLE `workflow_edge`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `workflow_id` bigint NOT NULL COMMENT '工作流ID',
  MODIFY COLUMN `source_node_id` bigint NOT NULL COMMENT '源节点ID',
  MODIFY COLUMN `target_node_id` bigint NOT NULL COMMENT '目标节点ID',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  COMMENT = '工作流连线';

ALTER TABLE `workflow_instance`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `instance_code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '实例编码',
  MODIFY COLUMN `workflow_code` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT '工作流编码',
  MODIFY COLUMN `definition_version` int NOT NULL COMMENT '定义版本',
  MODIFY COLUMN `run_type` varchar(32) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'MANUAL' COMMENT '运行类型',
  MODIFY COLUMN `status` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '状态',
  MODIFY COLUMN `parameters_json` text COLLATE utf8mb4_general_ci COMMENT '参数JSON',
  MODIFY COLUMN `started_at` timestamp NULL DEFAULT NULL COMMENT '开始时间',
  MODIFY COLUMN `finished_at` timestamp NULL DEFAULT NULL COMMENT '结束时间',
  MODIFY COLUMN `error_message` text COLLATE utf8mb4_general_ci COMMENT '错误消息',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  COMMENT = '工作流实例';

ALTER TABLE `workflow_node`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `workflow_id` bigint NOT NULL COMMENT '工作流ID',
  MODIFY COLUMN `node_code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '节点编码',
  MODIFY COLUMN `node_name` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT '节点名称',
  MODIFY COLUMN `node_type` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '节点类型',
  MODIFY COLUMN `dev_file_id` bigint DEFAULT NULL COMMENT '开发文件ID',
  MODIFY COLUMN `file_version_id` bigint DEFAULT NULL COMMENT '文件版本ID',
  MODIFY COLUMN `config_json` text COLLATE utf8mb4_general_ci COMMENT '配置JSON',
  MODIFY COLUMN `x` int DEFAULT NULL COMMENT '节点横坐标',
  MODIFY COLUMN `y` int DEFAULT NULL COMMENT '节点纵坐标',
  MODIFY COLUMN `timeout_seconds` int DEFAULT NULL COMMENT '超时秒',
  MODIFY COLUMN `retry_times` int DEFAULT '0' COMMENT '重试次数',
  MODIFY COLUMN `ds_task_code` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'DolphinScheduler 任务编码',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  COMMENT = '工作流节点';
