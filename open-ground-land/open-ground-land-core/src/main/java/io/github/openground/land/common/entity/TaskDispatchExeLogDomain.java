package io.github.openground.land.common.entity;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.openground.land.common.dao.BasePo;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
@TableName("TASK_DISPATCH_EXE_LOG")
public class TaskDispatchExeLogDomain extends BasePo {

    private String company;

    /** 流水ID */
    @TableId
    private String id;

    /** 任务ID */
    private String taskId;

    /** 计划开始时间 */
    private String planStartTime;

    /** 执行开始时间 */
    private String exeStartTime;

    /** 执行结束时间 */
    private String exeEndTime;

    /** 执行状态 */
    private String exeStatus;

    /** 异常信息 */
    private String errInfo;

    /** 执行本次任务主机IP */
    private String exeCurHostIp;

    /** 创建时间 */
    private String mtTime;

    /** 批次号 */
    private String batchNo;

    /** 文件名 */
    private String fileName;

    /** 作业ID */
    private String jobId;

    /** 跑批日期 */
    private String eodDate;

    /** 扩展字段 */
    private String extend1;
    private String extend2;
    private String extend3;
    private String extend4;
    private String extend5;
    private Map<String, Object> attacheds = new HashMap<String, Object>();

    public void buildAttachedStr() {
        this.setExtend5(JSON.toJSONString(attacheds));
    }

    public void parseAttacheds() {
        Map<String, Object> tmp = (Map<String, Object>) JSONObject.parse(getExtend5());
        if (tmp != null) {
            this.attacheds.clear();
            this.attacheds.putAll(tmp);
        }
    }
}
