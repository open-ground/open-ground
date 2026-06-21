package io.github.openground.base.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * <p>Title: BaseRequest</p>
 * <p>Description:  公共请求参数</p>
 *
 */
@Data
@Schema(description = "公共请求参数基类，所有 Request DTO 继承此类")
public class BaseRequest  implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 系统头信息
     */
    @Schema(description = "系统头信息，包含法人、用户ID、系统运行日期等")
    private SysHead sysHead;

    /**
     * 业务头信息
     */
    @Schema(description = "业务头信息，包含工程ID、分页参数等")
    private AppHead appHead;

    /**
     * 法人
     */
    @Schema(description = "法人")
    private String company;

    /**
     * 工程ID
     */
    @Schema(description = "工程ID")
    private String projectId;

}
