package io.github.openground.land.api.dto;

import io.github.openground.base.dto.BaseRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * 分段任务请求 DTO
 *
 * @author jack.zhang
 * @since 2026-06-25
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "分段任务请求参数")
public class TaskSegmentRequest extends BaseRequest {

    @Schema(description = "调度组")
    private String cpsGroup;

    @Schema(description = "执行计划ID")
    private String taskPlanId;

    @Schema(description = "查询类型")
    private String queryType;

    @Schema(description = "作业参数")
    private Map<String, Object> jobParam;

    @Schema(description = "步骤参数")
    private Map<String, Object> stepParam;

    @Schema(description = "执行上下文")
    private Map<String, Object> executorContext;
}
