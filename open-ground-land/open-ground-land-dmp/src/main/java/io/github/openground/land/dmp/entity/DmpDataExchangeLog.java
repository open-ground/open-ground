package io.github.openground.land.dmp.entity;

import lombok.Data;

import java.util.Date;

/**
 * 数据交换执行日志实体
 * <p>对应表 DMP_DATA_EXCHANGE_LOG，记录每次手动或定时执行的结果</p>
 *
 * @author open-ground
 * @since 1.0.5
 */
@Data
public class DmpDataExchangeLog {

    private Long id;
    private Long configId;
    private String taskType;    // FILE_TO_DB / DB_TO_FILE
    private Date startTime;
    private Date endTime;
    private Integer durationSeconds; // 耗时(秒)
    private String runStatus;    // SUCCESS / FAIL
    private Integer rowCount;    // 处理行数
    private String errorMsg;
    private String createBy;    // 执行人
    private Date createTime;
}
