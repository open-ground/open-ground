package io.github.openground.common.datasource.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 系统数据源配置实体
 *
 * <p>映射表 SYS_DATASOURCE。从 ground-auth-dmp 的 DmpDatasourceDO 迁移，
 * 表名从 dmp_datasource 改为 sys_datasource，作为框架级数据源配置。
 *
 * @author open-ground
 * @since 1.0.2
 */
@Data
@Schema(description = "系统数据源配置")
public class SysDatasourceDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "数据源名称")
    private String dsName;

    @Schema(description = "数据库类型：mysql/oracle/postgresql/dm/gaussdb")
    private String dbType;

    @Schema(description = "JDBC连接URL")
    private String jdbcUrl;

    @Schema(description = "驱动类名")
    private String driverClassName;

    @Schema(description = "主机地址（已废弃，使用 jdbcUrl 替代）")
    private String host;

    @Schema(description = "端口")
    private Integer port;

    @Schema(description = "数据库名")
    private String databaseName;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "密码（AES加密存储）")
    private String password;

    @Schema(description = "额外连接参数")
    private String connectParams;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "删除标记：0-正常 2-删除")
    private String delFlag;

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

    // ====== 查询参数 ======

    private Integer pageNum;
    private Integer pageSize;
}
