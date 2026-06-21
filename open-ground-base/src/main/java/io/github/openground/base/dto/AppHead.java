package io.github.openground.base.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <p>Title: SysHead</p>
 * <p>Description:  流程请求系统头信息</p>
 *
 * @author zhao.xiaobo
 * @version 3.0.0
 * @date 2019 -11-13 16:37
 */
@EqualsAndHashCode
@Data
@Schema(description = "业务头信息，包含工程ID、分页参数")
public class AppHead {

  /**
   * 当前页码
   */
  @Schema(description = "当前页码", example = "1")
  private int pageNum;

  /**
   * 每页数量
   */
  @Schema(description = "每页数量", example = "10")
  private int pageSize;

}
