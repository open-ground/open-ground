-- =============================================
-- 数据交换中心 - 配置表 DDL（MySQL）
-- 表名：TASK_DATA_EXCHANGE_CONFIG
-- 说明：存储数据交换任务的配置信息，包括
--       文件→库、库→文件、库→库三种模式
-- =============================================

DROP TABLE IF EXISTS `TASK_DATA_EXCHANGE_CONFIG`;
CREATE TABLE `TASK_DATA_EXCHANGE_CONFIG` (
    `id`                BIGINT          NOT NULL COMMENT '主键ID',
    `task_name`         VARCHAR(200)    NOT NULL COMMENT '任务名称',
    `task_type`         VARCHAR(20)     NOT NULL COMMENT '任务类型：FILE_TO_DB-文件入库 DB_TO_FILE-库导出 DB_TO_DB-库迁移',
    `source_ds_id`      BIGINT                   DEFAULT NULL COMMENT '源数据源ID（关联 sys_datasource），DB_TO_FILE/DB_TO_DB 时使用',
    `target_ds_id`      BIGINT                   DEFAULT NULL COMMENT '目标数据源ID（关联 sys_datasource），FILE_TO_DB/DB_TO_DB 时使用',
    `source_query`      TEXT                     DEFAULT NULL COMMENT '源端查询SQL（DB_TO_FILE/DB_TO_DB 时使用）',
    `source_table`      VARCHAR(200)             DEFAULT NULL COMMENT '源表名（DB_TO_DB 整表/条件导出时使用）',
    `source_file_path`  VARCHAR(500)             DEFAULT NULL COMMENT '源文件路径（FILE_TO_DB 时使用）',
    `target_table`      VARCHAR(200)             DEFAULT NULL COMMENT '目标表名（FILE_TO_DB/DB_TO_DB 时使用）',
    `target_file_path`  VARCHAR(500)             DEFAULT NULL COMMENT '目标文件路径（DB_TO_FILE 时使用）',
    `file_delimiter`    VARCHAR(10)              DEFAULT '|' COMMENT '文件分隔符，默认竖线',
    `file_encoding`     VARCHAR(20)              DEFAULT 'UTF-8' COMMENT '文件编码，默认 UTF-8',
    `write_mode`        VARCHAR(20)              DEFAULT 'APPEND' COMMENT '写入模式：APPEND-追加 TRUNCATE-先清后插 MERGE-存在更新不存在插入',
    `batch_size`        INT                      DEFAULT 2000 COMMENT '批量写入行数',
    `column_mappings`   TEXT                     DEFAULT NULL COMMENT '列映射JSON：[{"fileIndex":0,"sourceColumn":"...","targetColumn":"...","targetType":"..."}]',
    `thread_count`      INT                      DEFAULT 1 COMMENT '并行执行线程数',
    `task_status`       VARCHAR(10)              DEFAULT 'ENABLED' COMMENT '状态：ENABLED-启用 DISABLED-停用',
    `header_enabled`    CHAR(1)                  DEFAULT '0' COMMENT '是否输出表头行（DB_TO_FILE）：0-否 1-是',
    `done_file_enabled` CHAR(1)                  DEFAULT '1' COMMENT '是否生成 .ok 标识文件：0-否 1-是',
    `export_mode`       VARCHAR(20)              DEFAULT 'FULL_TABLE' COMMENT '导出模式（DB_TO_FILE）：FULL_TABLE-整表 CONDITIONAL-条件 CUSTOM_SQL-自定义SQL',
    `source_file_dir_id` BIGINT                  DEFAULT NULL COMMENT '源文件目录ID（关联 TASK_DATA_FILE_DIR），FILE_TO_DB 时使用',
    `target_file_dir_id` BIGINT                  DEFAULT NULL COMMENT '目标文件目录ID（关联 TASK_DATA_FILE_DIR），DB_TO_FILE 时使用',
    `source_system`     VARCHAR(100)              DEFAULT NULL COMMENT '文件来源系统（FILE_TO_DB 时使用，如：核心系统、信贷系统）',
    `del_flag`          CHAR(1)         NOT NULL DEFAULT '0' COMMENT '删除标记：0-正常 2-删除',
    `create_by`         VARCHAR(64)              DEFAULT NULL COMMENT '创建人',
    `create_time`       DATETIME                 DEFAULT NULL COMMENT '创建时间',
    `update_by`         VARCHAR(64)              DEFAULT NULL COMMENT '更新人',
    `update_time`       DATETIME                 DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_task_type` (`task_type`),
    INDEX `idx_source_ds_id` (`source_ds_id`),
    INDEX `idx_target_ds_id` (`target_ds_id`),
    INDEX `idx_status` (`task_status`),
    INDEX `idx_del_flag` (`del_flag`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB COMMENT='数据交换配置表';

-- =============================================
-- 数据交换中心 - 执行日志表 DDL（MySQL）
-- 表名：TASK_DATA_EXCHANGE_LOG
-- 说明：记录每次数据交换任务的执行历史和结果
-- =============================================

DROP TABLE IF EXISTS `TASK_DATA_EXCHANGE_LOG`;
CREATE TABLE `TASK_DATA_EXCHANGE_LOG` (
     `id`            BIGINT          NOT NULL COMMENT '主键ID',
     `config_id`     BIGINT          NOT NULL COMMENT '关联配置ID',
     `task_type`     VARCHAR(20)     NOT NULL COMMENT '任务类型：FILE_TO_DB-文件入库 DB_TO_FILE-库导出',
     `start_time`    DATETIME        NOT NULL COMMENT '开始执行时间',
     `end_time`      DATETIME                 DEFAULT NULL COMMENT '结束时间',
     `duration_seconds` INT                   DEFAULT 0 COMMENT '耗时(秒)',
     `run_status`    VARCHAR(10)     NOT NULL COMMENT '执行状态：SUCCESS-成功 FAIL-失败',
     `row_count`     INT             DEFAULT 0 COMMENT '处理行数',
     `error_msg`     TEXT                     DEFAULT NULL COMMENT '错误信息',
     `create_by`     VARCHAR(64)              DEFAULT NULL COMMENT '执行人',
     `create_time`   DATETIME                 DEFAULT NULL COMMENT '创建时间',
     PRIMARY KEY (`id`),
     INDEX `idx_config_id` (`config_id`),
     INDEX `idx_task_type` (`task_type`),
     INDEX `idx_run_status` (`run_status`),
     INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB COMMENT='数据交换执行日志表';

-- =============================================
-- 数据交换中心 - 文件目录管理表 DDL（MySQL）
-- 表名：TASK_DATA_FILE_DIR
-- 说明：统一管理数据交换中用到的文件目录路径，
--       配置任务时选择目录+输入文件名，一改全改
-- =============================================

DROP TABLE IF EXISTS `TASK_DATA_FILE_DIR`;
CREATE TABLE `TASK_DATA_FILE_DIR` (
    `id`            BIGINT          NOT NULL COMMENT '主键ID',
    `dir_name`      VARCHAR(100)    NOT NULL COMMENT '目录名称（便于识别，如：数据导入目录、导出目录）',
    `dir_path`      VARCHAR(500)    NOT NULL COMMENT '目录路径（如：/data/files/input）',
    `dir_type`      VARCHAR(20)     DEFAULT 'LOCAL' COMMENT '目录类型：LOCAL-本地 SFTP-远程SFTP FTP-远程FTP',
    `remark`        VARCHAR(500)             DEFAULT NULL COMMENT '备注',
    `del_flag`      CHAR(1)         NOT NULL DEFAULT '0' COMMENT '删除标记：0-正常 1-删除',
    `create_by`     VARCHAR(64)              DEFAULT NULL COMMENT '创建人',
    `create_time`   DATETIME                 DEFAULT NULL COMMENT '创建时间',
    `update_by`     VARCHAR(64)              DEFAULT NULL COMMENT '更新人',
    `update_time`   DATETIME                 DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_dir_type` (`dir_type`),
    INDEX `idx_del_flag` (`del_flag`)
) ENGINE=InnoDB COMMENT='文件目录管理表';