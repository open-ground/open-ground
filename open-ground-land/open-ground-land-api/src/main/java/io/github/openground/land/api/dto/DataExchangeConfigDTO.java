package io.github.openground.land.api.dto;

import io.github.openground.base.dto.BaseRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 数据交换配置 DTO
 *
 * @author jack.zhang
 * @since 2026-07-17
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "数据交换配置")
public class DataExchangeConfigDTO extends BaseRequest {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "任务名称")
    private String taskName;

    @Schema(description = "任务类型：FILE_TO_DB / DB_TO_FILE / DB_TO_DB")
    private String taskType;

    @Schema(description = "源数据源ID")
    private Long sourceDsId;

    @Schema(description = "目标数据源ID")
    private Long targetDsId;

    @Schema(description = "源端查询SQL（库→库/库→文件时使用）")
    private String sourceQuery;

    @Schema(description = "源表名（库→库整表/条件导出时使用）")
    private String sourceTable;

    @Schema(description = "源文件路径（文件→库时使用）")
    private String sourceFilePath;

    @Schema(description = "目标表名")
    private String targetTable;

    @Schema(description = "目标文件路径（库→文件时使用）")
    private String targetFilePath;

    @Schema(description = "文件分隔符，默认 |")
    private String fileDelimiter;

    @Schema(description = "文件编码，默认 UTF-8")
    private String fileEncoding;

    @Schema(description = "写入模式：APPEND / TRUNCATE / MERGE")
    private String writeMode;

    @Schema(description = "批大小，默认 2000")
    private Integer batchSize;

    @Schema(description = "列映射 JSON")
    private String columnMappings;

    @Schema(description = "并行线程数")
    private Integer threadCount;

    @Schema(description = "状态：ENABLED / DISABLED")
    private String taskStatus;

    // 分页
    @Schema(description = "第几页")
    private int pageIndex;

    @Schema(description = "每页条数")
    private int pageSize;
}
