-- ============================================================
-- 数据源表权限控制
-- 表名：sys_datasource_table_permission
-- 说明：配置每个数据源按角色的表可见范围
-- 行为：某数据源存在记录时，用户仅能查看其角色所配置的表
--       某数据源无记录时，所有用户均看不到任何表（严格模式）
-- ============================================================
CREATE TABLE sys_datasource_table_permission
(
    id              BIGINT       NOT NULL COMMENT '主键ID',
    datasource_id   BIGINT       NOT NULL COMMENT '数据源ID，关联 sys_datasource.id',
    role_id         VARCHAR(64)  NOT NULL COMMENT '角色ID',
    table_name      VARCHAR(255) NOT NULL COMMENT '表名',
    create_by       VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
    create_time     DATETIME     NOT NULL COMMENT '创建时间',
    update_by       VARCHAR(64)  DEFAULT NULL COMMENT '更新人',
    update_time     DATETIME     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ds_role_table (datasource_id, role_id, table_name),
    KEY             idx_datasource_id (datasource_id),
    KEY             idx_role_id (role_id)
) COMMENT='数据源表权限控制';
