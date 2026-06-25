package io.github.openground.base.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <p>Title: SysHead</p>
 * <p>Description:  流程请求系统头信息</p>
 *
 * @author jack.zhang
 * @version 3.0.0
 * @date 2019 -11-13 16:37
 */
@EqualsAndHashCode
@Data
@Schema(description = "系统头信息，包含法人、用户、交易日期等公共字段")
public class SysHead {

  /**
   * 交易日期
   */
  @Schema(description = "交易日期", example = "20250101")
  private String tranDate;
  /**
   * 渠道流水号<br>
   */
  @Schema(description = "渠道流水号")
  private String seqNo;

  /**
   * 分支行标识<br>
   */
  @Schema(description = "分支行标识")
  private String branchId;

  /**
   * 柜员标识<br>
   */
  @Schema(description = "用户ID")
  private String userId;

  /**
   * 传输密押<br>
   */
  @Schema(description = "传输密押")
  private String macValue;

  /**
   * 渠道类型<br>
   */
  @Schema(description = "渠道类型")
  private String sourceType;

  /**
   * 操作员语言<br> CHINESE－中文；<br> AMERICAN/ENGLISH－英文；<br>
   */
  @Schema(description = "操作员语言", allowableValues = {"CHINESE", "AMERICAN", "ENGLISH"})
  private String userLang;

  /**
   * 交易时间<br>
   */
  @Schema(description = "交易时间")
  private String tranTimestamp;

  /**
   * 法人
   */
  @Schema(description = "法人")
  private String company;

  /**
   * 系统运行日期
   */
  @Schema(description = "系统运行日期", example = "20250101")
  private String sysRunDate;


}
