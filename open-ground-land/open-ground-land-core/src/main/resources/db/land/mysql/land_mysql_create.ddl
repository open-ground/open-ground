
-- ----------------------------
-- Table structure for task_dispatch_active_host
-- ----------------------------
DROP TABLE IF EXISTS task_dispatch_active_host;
CREATE TABLE task_dispatch_active_host
(
    CPS_GROUP     varchar(32) NOT NULL COMMENT '调度组',
    HOST_IP       varchar(60) NOT NULL COMMENT '主机IP',
    ACTIVE_TIME   varchar(19) DEFAULT NULL COMMENT '最后活动时间',
    ACTIVE_STATUS varchar(3)  DEFAULT NULL COMMENT '活动状态 ON-开启；OFF-关闭',
    SYS_EOD_DATE  varchar(10) NOT NULL COMMENT '跑批日期',
    PRIMARY KEY (HOST_IP, CPS_GROUP) USING BTREE,
    UNIQUE INDEX P_KEY_1(HOST_IP, CPS_GROUP) USING BTREE
) ENGINE = InnoDB  COMMENT = '定时任务主机表' ;

-- ----------------------------
-- Table structure for task_dispatch_config
-- ----------------------------
DROP TABLE IF EXISTS task_dispatch_config;
CREATE TABLE task_dispatch_config
(
    TASK_ID          varchar(100)   NOT NULL COMMENT '任务ID',
    TASK_NAME        varchar(100)   NOT NULL COMMENT '任务名称',
    BEFORE_TASK      varchar(512)   DEFAULT NULL COMMENT '前置任务',
    AFTER_TASK       varchar(512)   DEFAULT NULL COMMENT '后置任务',
    IS_BEFORE        varchar(2)     DEFAULT NULL COMMENT '是否有前置任务',
    TASK_ADDRESS     text COMMENT '任务执行地址',
    TASK_METHOD      text           NOT NULL COMMENT '执行service',
    EXE_PERIOD_VALUE varchar(10)    DEFAULT NULL COMMENT '执行周期值',
    EXE_PERIOD_TYPE  varchar(10)    DEFAULT NULL COMMENT '执行周期类型',
    FIRST_EXE_TIME   varchar(19)    DEFAULT NULL COMMENT '第一次执行时间',
    STATUS           varchar(10)    NOT NULL COMMENT '任务是否有效',
    EXE_HOST_IP      varchar(60)    DEFAULT NULL COMMENT '执行任务主机IP',
    NEXT_EXE_TIME    varchar(19)    DEFAULT NULL COMMENT '下次执行时间',
    START_TIME       varchar(19)    NOT NULL COMMENT '开始执行时间',
    END_TIME         varchar(19)    NOT NULL COMMENT '结束执行时间',
    ADDITION_FLAG    varchar(2)     NOT NULL COMMENT '是否追加标志 Y:启动时追加历史未执行，N：否',
    TASK_IS_RUNNING  varchar(1)     DEFAULT NULL COMMENT '任务是否正在执行',
    TASK_DETAIL      text COMMENT '介绍任务明细',
    MT_TIME          varchar(19)    DEFAULT NULL COMMENT '创建时间',
    MT_USER          varchar(10)    DEFAULT NULL COMMENT '创建人',
    RE_EXE_TIMES     decimal(11, 0) DEFAULT NULL COMMENT '已重跑次数',
    MAX_RE_EXE_TIMES decimal(11, 0) NOT NULL COMMENT '最大重跑次数',
    PARAMS           text COMMENT '传入参数',
    BATCH_NO         decimal(11, 0) DEFAULT NULL COMMENT '批次号',
    UPDATE_TIME      varchar(20)    DEFAULT NULL COMMENT '更新时间',
    FILE_INFO        varchar(255)   DEFAULT NULL COMMENT '文件信息',
    CONDITION_PARAM  varchar(64)    DEFAULT NULL COMMENT '条件依赖参数ID',
    CPS_GROUP        varchar(32)    DEFAULT NULL COMMENT '调度组',
    COMPANY          varchar(50)    DEFAULT NULL COMMENT '法人',
    JOB_ID           varchar(100)   DEFAULT NULL COMMENT '作业ID',
    JOB_FLOW         varchar(1024)  DEFAULT NULL COMMENT '作业流程图配置',
    EXTEND1          varchar(255)   DEFAULT NULL COMMENT '扩展字段',
    EXTEND2          varchar(255)   DEFAULT NULL COMMENT '扩展字段',
    EXTEND3          varchar(255)   DEFAULT NULL COMMENT '扩展字段',
    EXTEND4          varchar(255)   DEFAULT NULL COMMENT '扩展字段',
    EXTEND5          varchar(255)   DEFAULT NULL COMMENT '扩展字段',
    PRIMARY KEY (TASK_ID) USING BTREE
) ENGINE = InnoDB  COMMENT = '任务配置表';

-- ----------------------------
-- Table structure for task_dispatch_exe_log
-- ----------------------------
DROP TABLE IF EXISTS task_dispatch_exe_log;
CREATE TABLE task_dispatch_exe_log
(
    ID              varchar(20) NOT NULL COMMENT '流水ID',
    JOB_ID          varchar(100) DEFAULT NULL COMMENT '作业编码',
    EOD_DATE        varchar(20)  DEFAULT NULL COMMENT '跑批日期',
    TASK_ID         varchar(100) DEFAULT NULL COMMENT '任务ID',
    PLAN_START_TIME varchar(19)  DEFAULT NULL COMMENT '本次任务计划开始时间',
    EXE_START_TIME  varchar(19)  DEFAULT NULL COMMENT '执行开始时间',
    EXE_END_TIME    varchar(19)  DEFAULT NULL COMMENT '执行结束时间',
    EXE_STATUS      varchar(1)   DEFAULT NULL COMMENT '执行状态: S：正常终了； E：异常终了； P：待执行； R：执行中； I：执行计划被忽略',
    ERR_INFO        text COMMENT '异常信息',
    EXE_CUR_HOST_IP varchar(60)  DEFAULT NULL COMMENT '执行本次任务主机IP',
    MT_TIME         varchar(19)  DEFAULT NULL COMMENT '创建时间',
    BATCH_NO        varchar(15)  DEFAULT NULL COMMENT '批次号',
    FILE_NAME       varchar(255) DEFAULT NULL COMMENT '文件名',
    EXTEND1         varchar(255) DEFAULT NULL COMMENT '文件路径',
    EXTEND2         varchar(255) DEFAULT NULL COMMENT '计划是否可忽略，Y-可忽略，N-不可忽略',
    EXTEND3         varchar(255) DEFAULT NULL,
    EXTEND4         varchar(255) DEFAULT NULL,
    EXTEND5         varchar(255) DEFAULT NULL,
    COMPANY         varchar(50)  DEFAULT NULL COMMENT '法人',
    PRIMARY KEY (ID) USING BTREE
) ENGINE = InnoDB  COMMENT = '任务执行计划表' ;

-- ----------------------------
-- Table structure for task_dispatch_param
-- ----------------------------
DROP TABLE IF EXISTS task_dispatch_param;
CREATE TABLE task_dispatch_param
(
    PARAM_ID        varchar(50)  NOT NULL COMMENT 'id',
    PARAM_NAME      varchar(255) DEFAULT NULL COMMENT '名称',
    HOST_IP         varchar(32)  NOT NULL COMMENT 'IP地址',
    PORT            varchar(10)  DEFAULT NULL COMMENT '端口',
    USERNAME        varchar(20)  DEFAULT NULL COMMENT '用户名',
    PASSWORD        varchar(64)  DEFAULT NULL COMMENT '密码',
    ENCODING        varchar(20)  NOT NULL COMMENT '字符集',
    IS_DYNAMIC_PATH varchar(255) DEFAULT NULL COMMENT '是否动态路径',
    PATH_ROLE       varchar(255) DEFAULT NULL COMMENT '路径规则',
    REMOTE_PATH     varchar(255) NOT NULL COMMENT '远程路径',
    IS_DYNAME_NAME  varchar(10)  DEFAULT NULL COMMENT '是否动态文件名',
    NAME_ROLE       varchar(255) DEFAULT NULL COMMENT '文件名规则',
    FILE_NAME       varchar(255) NOT NULL COMMENT '文件名',
    LOCAL_PATH      varchar(255) NOT NULL COMMENT '本地路径',
    INSERT_TIME     datetime(0) DEFAULT NULL COMMENT '插入时间',
    UPDATE_TIME     datetime(0) DEFAULT NULL COMMENT '修改时间',
    IS_IGNORE       varchar(10)  DEFAULT NULL COMMENT '是否可忽略',
    EXTEND1         varchar(255) DEFAULT NULL COMMENT '扩展1',
    EXTEND2         varchar(255) DEFAULT NULL COMMENT '扩展2',
    EXTEND3         varchar(255) DEFAULT NULL COMMENT '扩展3',
    COMPANY         varchar(50)  DEFAULT NULL COMMENT '法人',
    PRIMARY KEY (PARAM_ID) USING BTREE
) ENGINE = InnoDB  COMMENT = '任务参数表';

