package io.github.openground.land.dmp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.openground.land.common.dao.BasePo;
import lombok.Data;

import java.util.Date;

/**
 * 数据交换执行日志实体
 * <p>对应表 TASK_DATA_EXCHANGE_LOG，记录每次手动或定时执行的结果，存储在 land 框架数据源中</p>
 *
 * @author open-ground
 * @since 1.0.5
 */
@Data
@TableName("TASK_DATA_EXCHANGE_LOG")
public class TaskDataExchangeLog extends BasePo {

    /** 主键ID */
    @TableId(type = IdType.INPUT)
    private Long id;

    /** 关联的交换配置ID */
    private Long configId;

    /** 任务类型：FILE_TO_DB / DB_TO_FILE */
    private String taskType;

    /** 执行开始时间 */
    private Date startTime;

    /** 执行结束时间 */
    private Date endTime;

    /** 执行耗时(秒) */
    private Integer durationSeconds;

    /** 运行状态：SUCCESS / FAIL */
    private String runStatus;

    /** 处理行数 */
    private Integer rowCount;

    /** 错误信息 */
    private String errorMsg;

    /** 执行人 */
    private String createBy;

    /** 创建时间 */
    private Date createTime;
}
