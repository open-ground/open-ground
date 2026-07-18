package io.github.openground.land.dmp.entity;

import lombok.Data;

import java.util.Date;

/**
 * 数据交换配置实体
 * <p>对应表 DMP_DATA_EXCHANGE_CONFIG，存储在共享中心库中</p>
 *
 * @author jack.zhang
 * @since 2026-07-17
 */
@Data
public class DmpDataExchangeConfig {

    private String id;
    private String taskName;
    private String taskType;        // FILE_TO_DB / DB_TO_FILE / DB_TO_DB
    private Long sourceDsId;        // 源数据源ID
    private Long targetDsId;        // 目标数据源ID
    private String sourceQuery;     // 源端查询SQL
    private String sourceFilePath;  // 源文件路径（FILE_TO_DB）
    private String targetTable;     // 目标表名
    private String targetFilePath;  // 目标文件路径（DB_TO_FILE）
    private String fileDelimiter;   // 文件分隔符
    private String fileEncoding;    // 文件编码
    private String writeMode;       // APPEND / TRUNCATE / MERGE
    private Integer batchSize;      // 批大小
    private String columnMappings;  // 列映射JSON
    private Integer threadCount;    // 并行线程数
    private String status;          // ENABLED / DISABLED

    private String createBy;
    private Date createTime;
    private String updateBy;
    private Date updateTime;
    private String delFlag;
}
