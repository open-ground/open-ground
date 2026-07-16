package io.github.openground.land.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 任务监控响应 DTO
 *
 * @author jack.zhang
 * @since 2026-07-16
 */
@Data
@Schema(description = "任务监控响应")
public class TaskMonitorResponse {

    @Schema(description = "数据库连接池信息")
    private List<Map<String, Object>> dataSourceInfo;

    @Schema(description = "调度主线程池信息")
    private Map<String, Object> dispatchThreadPool;

    @Schema(description = "任务执行线程池信息")
    private Map<String, Object> taskThreadPool;

    @Schema(description = "JVM 运行情况")
    private Map<String, Object> jvmInfo;

    @Schema(description = "线程情况")
    private List threadList;

    @Schema(description = "当前刷新时间")
    private String currentTime;
}
