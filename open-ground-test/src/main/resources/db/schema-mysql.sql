-- ============================================================
-- Open Ground 测试模块 —— MySQL 建表脚本
-- 启动时由 spring.sql.init.schema-locations 自动执行
-- ============================================================

-- 会话表
CREATE TABLE IF NOT EXISTS sys_session (
    id VARCHAR(64) PRIMARY KEY COMMENT '会话ID',
    session_data TEXT COMMENT '会话数据（JSON）',
    token VARCHAR(255) NOT NULL COMMENT 'Token',
    username VARCHAR(100) NOT NULL COMMENT '用户名',
    grant_type VARCHAR(50) COMMENT '授权类型',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    last_access_time DATETIME COMMENT '最后访问时间',
    expire_time BIGINT NOT NULL COMMENT '过期时间（yyyyMMddHHmmss）',
    host VARCHAR(100) COMMENT '客户端IP',
    INDEX idx_session_username (username),
    INDEX idx_session_token (token),
    INDEX idx_session_expire_time (expire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话表';

-- API Key 表
CREATE TABLE IF NOT EXISTS sys_api_key (
    id VARCHAR(64) PRIMARY KEY COMMENT 'Key ID',
    user_id VARCHAR(64) NOT NULL COMMENT '用户ID',
    username VARCHAR(100) NOT NULL COMMENT '用户名',
    api_key VARCHAR(255) NOT NULL COMMENT 'API Key',
    name VARCHAR(100) COMMENT 'Key 名称',
    status TINYINT DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    expire_time DATETIME COMMENT '过期时间',
    last_used_time DATETIME COMMENT '最后使用时间',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    create_by VARCHAR(100) COMMENT '创建人',
    update_by VARCHAR(100) COMMENT '更新人',
    UNIQUE INDEX uk_api_key (api_key),
    INDEX idx_apikey_username (username),
    INDEX idx_apikey_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API Key 表';

-- 序列主键表（KeyGenerator 依赖）
CREATE TABLE IF NOT EXISTS sys_auto_pmkey (
    pk_name VARCHAR(128) NOT NULL COMMENT '序列名称',
    max_value BIGINT NOT NULL DEFAULT 0 COMMENT '当前最大值',
    step_len INT NOT NULL DEFAULT 1 COMMENT '步长',
    pk_len INT NOT NULL DEFAULT 10 COMMENT '序号长度（填充后长度）',
    sys_cd VARCHAR(32) DEFAULT '' COMMENT '所属系统编码',
    prefix VARCHAR(32) DEFAULT '' COMMENT '前缀',
    reset_date VARCHAR(16) DEFAULT '' COMMENT '最近重置日期（yyyyMMdd）',
    reset_freq VARCHAR(16) DEFAULT '' COMMENT '重置频率（daily/monthly）',
    remark VARCHAR(256) DEFAULT '' COMMENT '备注',
    PRIMARY KEY (pk_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='序列主键表';

-- 初始化测试序列数据
INSERT IGNORE INTO sys_auto_pmkey (pk_name, max_value, step_len, pk_len, sys_cd, prefix, reset_date, reset_freq, remark)
VALUES ('TEST_SEQ_01', 0, 1, 6, 'TEST', 'TS', '', '', '测试用序列');
INSERT IGNORE INTO sys_auto_pmkey (pk_name, max_value, step_len, pk_len, sys_cd, prefix, reset_date, reset_freq, remark)
VALUES ('TEST_BUSINESS_KEY', 0, 1, 8, 'TEST', 'BIZ', '', '', '测试用业务流水号');
INSERT IGNORE INTO sys_auto_pmkey (pk_name, max_value, step_len, pk_len, sys_cd, prefix, reset_date, reset_freq, remark)
VALUES ('TEST_USER_ID', 1000, 1, 6, '', 'U', '', '', '测试用户ID序列');

-- 操作日志表（JdbcLogSender 依赖）
CREATE TABLE IF NOT EXISTS sys_opt_log (
    log_id VARCHAR(64) PRIMARY KEY COMMENT '日志ID',
    opt_type VARCHAR(32) COMMENT '操作类型',
    opt_url VARCHAR(512) COMMENT '请求URL',
    opt_remark VARCHAR(512) COMMENT '操作说明',
    opt_method VARCHAR(256) COMMENT '操作方法',
    opt_param TEXT COMMENT '请求参数',
    user_id VARCHAR(100) COMMENT '操作人',
    ip_address VARCHAR(64) COMMENT 'IP地址',
    opt_status VARCHAR(2) COMMENT '操作状态：S-成功 F-失败',
    err_msg TEXT COMMENT '错误信息',
    sys_time VARCHAR(32) COMMENT '操作时间',
    INDEX idx_opt_log_type (opt_type),
    INDEX idx_opt_log_status (opt_status),
    INDEX idx_opt_log_time (sys_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';
