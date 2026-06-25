CREATE TABLE sys_db_check_log
(
    id              VARCHAR(32) NOT NULL COMMENT '主键ID',
    operation_type  VARCHAR(20) NOT NULL COMMENT '操作类型：check/sync',
    db_type         VARCHAR(20)   DEFAULT NULL COMMENT '数据库类型',
    script_count    INT           DEFAULT 0 COMMENT '脚本数量',
    mismatch_count  INT           DEFAULT 0 COMMENT '不一致行数',
    extra_row_count INT           DEFAULT 0 COMMENT '多余数据行数',
    sql_count       INT           DEFAULT 0 COMMENT '生成 SQL 条数',
    executed        CHAR(1)       DEFAULT '0' COMMENT '是否执行同步：0-否 1-是',
    success         CHAR(1)       DEFAULT '1' COMMENT '是否成功：0-失败 1-成功',
    error_message   VARCHAR(2000) DEFAULT NULL COMMENT '错误信息',
    cost_ms         BIGINT        DEFAULT 0 COMMENT '执行耗时(毫秒)',
    create_by       VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
    create_time     DATETIME    NOT NULL COMMENT '创建时间',
    update_time     DATETIME      DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY             idx_operation_type (operation_type),
    KEY             idx_create_time (create_time)
);