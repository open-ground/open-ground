package io.github.openground.land.api.dto;

import io.github.openground.base.dto.BaseRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 任务参数请求 DTO
 *
 * @author jack.zhang
 * @since 2026-06-25
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "任务参数请求参数")
public class TaskDispatchParamRequest extends BaseRequest {

    @Schema(description = "关键字（模糊搜索）")
    private String keyWord;

    @Schema(description = "调度组")
    private String cpsGroup;

    @Schema(description = "参数ID")
    private String paramId;

    @Schema(description = "参数名称")
    private String paramName;

    @Schema(description = "主机IP")
    private String hostIp;

    @Schema(description = "端口")
    private String port;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "密码")
    private String password;

    @Schema(description = "编码")
    private String encoding;

    @Schema(description = "是否动态路径")
    private String isDynamicPath;

    @Schema(description = "路径规则")
    private String pathRole;

    @Schema(description = "远程路径")
    private String remotePath;

    @Schema(description = "是否动态名称")
    private String isDynameName;

    @Schema(description = "名称规则")
    private String nameRole;

    @Schema(description = "文件名")
    private String fileName;

    @Schema(description = "本地路径")
    private String localPath;

    @Schema(description = "是否忽略")
    private String isIgnore;

    // ==================== 分页 ====================

    @Schema(description = "第几页")
    private int pageIndex;

    @Schema(description = "每页条数")
    private int pageSize;
}
