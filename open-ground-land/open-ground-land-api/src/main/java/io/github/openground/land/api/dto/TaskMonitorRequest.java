package io.github.openground.land.api.dto;

import io.github.openground.base.dto.BaseRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 任务监控请求 DTO
 *
 * @author jack.zhang
 * @since 2026-07-16
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "任务监控请求参数")
public class TaskMonitorRequest extends BaseRequest {

    @Schema(description = "调度组")
    private String cpsGroup;

    @Schema(description = "服务 URL（用于转发到目标实例）")
    private String serviceUrl;

    @Schema(description = "系统跑批日期")
    private String sysEodDate;
}
