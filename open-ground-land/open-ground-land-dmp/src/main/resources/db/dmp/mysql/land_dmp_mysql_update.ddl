-- 2. 配置表新增文件目录ID字段
ALTER TABLE `TASK_DATA_EXCHANGE_CONFIG`
    ADD COLUMN `source_file_dir_id` BIGINT DEFAULT NULL COMMENT '源文件目录ID（关联 TASK_DATA_FILE_DIR），FILE_TO_DB 时使用' AFTER `export_mode`,
    ADD COLUMN `target_file_dir_id` BIGINT DEFAULT NULL COMMENT '目标文件目录ID（关联 TASK_DATA_FILE_DIR），DB_TO_FILE 时使用' AFTER `source_file_dir_id`;
ALTER TABLE `TASK_DATA_EXCHANGE_CONFIG`
    ADD COLUMN `source_system` VARCHAR(100) DEFAULT NULL COMMENT '文件来源系统（FILE_TO_DB 时使用，如：核心系统、信贷系统）' AFTER `target_file_dir_id`;