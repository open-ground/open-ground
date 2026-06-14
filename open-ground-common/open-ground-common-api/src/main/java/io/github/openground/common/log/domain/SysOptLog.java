package io.github.openground.common.log.domain;

import lombok.Data;

import java.io.Serializable;

/**
 * 操作日志记录 DTO
 * <p>对应数据库表 sys_opt_log</p>
 *
 * @author open-ground
 * @version 1.0
 */
@Data
public class SysOptLog implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 日志ID */
    private String logId;
    /** 操作人 */
    private String userId;
    /** IP 地址 */
    private String ipAddress;
    /** 操作类型 */
    private String optType;
    /** 请求 URL */
    private String optUrl;
    /** 操作说明 */
    private String optRemark;
    /** 操作方法（类名.方法名） */
    private String optMethod;
    /** 请求参数 */
    private String optParam;
    /** 操作状态：S-成功，F-失败 */
    private String optStatus;
    /** 错误信息 */
    private String errMsg;
    /** 操作时间 */
    private String sysTime;

    // ===== 查询条件字段 =====

    /** 关键字 */
    private String keyWords;
    /** 页码 */
    private String pageIndex;
    /** 每页条数 */
    private String pageSize;
    /** 开始时间 */
    private String startTime;
    /** 结束时间 */
    private String endTime;
}
