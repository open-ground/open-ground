-- Open Ground Security 数据库脚本
-- 支持 MySQL、Oracle、达梦、PostgreSQL 等多数据库

-- 会话表
CREATE TABLE sys_session (
    id VARCHAR(64) PRIMARY KEY COMMENT '会话ID',
    session_data TEXT COMMENT '会话数据（JSON）',
    token VARCHAR(255) NOT NULL COMMENT 'Token',
    username VARCHAR(100) NOT NULL COMMENT '用户名',
    grant_type VARCHAR(50) COMMENT '授权类型',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    last_access_time DATETIME COMMENT '最后访问时间',
    expire_time DATETIME NOT NULL COMMENT '过期时间',
    host VARCHAR(100) COMMENT '客户端IP',
    INDEX idx_username (username),
    INDEX idx_token (token),
    INDEX idx_expire_time (expire_time)
) COMMENT '会话表';

-- API Key 表
CREATE TABLE sys_api_key (
    id VARCHAR(64) PRIMARY KEY COMMENT 'Key ID',
    user_id VARCHAR(64) NOT NULL COMMENT '用户ID',
    username VARCHAR(100) NOT NULL COMMENT '用户名',
    api_key VARCHAR(255) NOT NULL COMMENT 'API Key',
    name VARCHAR(100) COMMENT 'Key 名称',
    status VARCHAR(2) DEFAULT '0' COMMENT '状态：0-启用，1-禁用',
    expire_time BIGINT COMMENT '过期时间戳（毫秒）',
    last_used_time DATETIME COMMENT '最后使用时间',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    create_by VARCHAR(100) COMMENT '创建人',
    update_by VARCHAR(100) COMMENT '更新人',
    UNIQUE INDEX uk_api_key (api_key),
    INDEX idx_username (username),
    INDEX idx_user_id (user_id)
) COMMENT 'API Key 表';