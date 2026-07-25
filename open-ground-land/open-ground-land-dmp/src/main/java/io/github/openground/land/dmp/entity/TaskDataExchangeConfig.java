package io.github.openground.land.dmp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.openground.land.common.dao.BasePo;
import lombok.Data;

import java.util.Date;

/**
 * 数据交换配置实体
 * <p>对应表 TASK_DATA_EXCHANGE_CONFIG，存储在 land 框架数据源中</p>
 *
 * @author jack.zhang
 * @since 1.0.6
 */
@Data
@TableName("TASK_DATA_EXCHANGE_CONFIG")
public class TaskDataExchangeConfig extends BasePo {

    /** 主键ID */
    @TableId(type = IdType.INPUT)
    private Long id;

    /** 任务名称 */
    private String taskName;

    /** 任务类型：FILE_TO_DB / DB_TO_FILE / DB_TO_DB */
    private String taskType;

    /** 源数据源ID */
    private Long sourceDsId;

    /** 目标数据源ID */
    private Long targetDsId;

    /** 源端查询SQL / WHERE条件 */
    private String sourceQuery;

    /** 源表名（DB_TO_DB，1.0.7 新增） */
    private String sourceTable;

    /** 源文件路径（FILE_TO_DB） */
    private String sourceFilePath;

    /** 目标表名 */
    private String targetTable;

    /** 目标文件路径（DB_TO_FILE） */
    private String targetFilePath;

    /** 文件分隔符 */
    private String fileDelimiter;

    /** 文件编码 */
    private String fileEncoding;

    /** 写入模式：APPEND / TRUNCATE / MERGE */
    private String writeMode;

    /** 批大小 */
    private Integer batchSize;

    /** 列映射JSON */
    private String columnMappings;

    /** 并行线程数 */
    private Integer threadCount;

    /** 任务状态：ENABLED / DISABLED */
    private String taskStatus;

    /** 是否输出表头行：0-否 1-是（DB_TO_FILE，1.0.5 新增） */
    private String headerEnabled;

    /** 是否生成 .ok 标识文件：0-否 1-是（1.0.5 新增） */
    private String doneFileEnabled;

    /** 导出模式：FULL_TABLE / CONDITIONAL / CUSTOM_SQL（DB_TO_FILE，1.0.5 新增） */
    private String exportMode;

    /** 源文件目录ID（关联 TASK_DATA_FILE_DIR），FILE_TO_DB 时使用 */
    private Long sourceFileDirId;

    /** 目标文件目录ID（关联 TASK_DATA_FILE_DIR），DB_TO_FILE 时使用 */
    private Long targetFileDirId;

    /** 文件来源系统（FILE_TO_DB 时使用，如：核心系统、信贷系统） */
    private String sourceSystem;

    /** 创建人 */
    private String createBy;

    /** 创建时间 */
    private Date createTime;

    /** 更新人 */
    private String updateBy;

    /** 更新时间 */
    private Date updateTime;

    /** 逻辑删除标志 */
    @TableLogic
    private String delFlag;
}
