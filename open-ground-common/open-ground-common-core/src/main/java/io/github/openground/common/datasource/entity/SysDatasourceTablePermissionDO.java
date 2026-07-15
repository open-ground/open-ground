package io.github.openground.common.datasource.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 数据源表权限实体
 *
 * <p>映射表 SYS_DATASOURCE_TABLE_PERMISSION。
 * 配置每个数据源按角色的表可见范围。</p>
 *
 * @author open-ground
 * @since 1.0.4
 */
@Data
@Schema(description = "数据源表权限")
public class SysDatasourceTablePermissionDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "数据源ID")
    private Long datasourceId;

    @Schema(description = "角色ID")
    private String roleId;

    @Schema(description = "表名")
    private String tableName;

    @Schema(description = "创建人")
    private String createBy;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    @Schema(description = "更新人")
    private String updateBy;

    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;
}
