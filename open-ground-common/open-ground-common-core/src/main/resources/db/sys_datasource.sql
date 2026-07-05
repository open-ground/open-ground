-- ===============================
-- Table: sys_datasource
-- 系统数据源配置表（从 dmp_datasource 迁移）
-- ===============================
DROP TABLE IF EXISTS `sys_datasource`;
CREATE TABLE `sys_datasource` (
    `id`                BIGINT          NOT NULL COMMENT '主键ID',
    `ds_name`           VARCHAR(100)    NOT NULL COMMENT '数据源名称',
    `db_type`           VARCHAR(20)             DEFAULT NULL COMMENT '数据库类型：mysql/oracle/postgresql/dm/gaussdb',
    `jdbc_url`          VARCHAR(500)             DEFAULT NULL COMMENT 'JDBC连接URL',
    `driver_class_name` VARCHAR(200)             DEFAULT NULL COMMENT '驱动类名',
    `host`              VARCHAR(100)             DEFAULT NULL COMMENT '主机地址（已废弃，使用jdbcUrl替代）',
    `port`              INT                      DEFAULT NULL COMMENT '端口',
    `database_name`     VARCHAR(100)             DEFAULT NULL COMMENT '数据库名',
    `username`          VARCHAR(100)             DEFAULT NULL COMMENT '用户名',
    `password`          VARCHAR(500)             DEFAULT NULL COMMENT '密码（AES加密存储）',
    `connect_params`    VARCHAR(500)             DEFAULT NULL COMMENT '额外连接参数',
    `remark`            VARCHAR(200)             DEFAULT NULL COMMENT '备注',
    `del_flag`          CHAR(1)         NOT NULL DEFAULT '0' COMMENT '删除标记：0-正常 2-删除',
    `create_by`         VARCHAR(50)              DEFAULT NULL COMMENT '创建人',
    `create_time`       DATETIME                 DEFAULT NULL COMMENT '创建时间',
    `update_by`         VARCHAR(50)              DEFAULT NULL COMMENT '更新人',
    `update_time`       DATETIME                 DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_datasource_ds_name` (`ds_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统数据源配置表';
