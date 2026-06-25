package io.github.openground.land.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.openground.land.common.dao.BasePo;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @Author jack.zhang
 * @Description 任务参数表
 * @Date 2022-04-20 16:11:35
 * @Version 1.0
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("TASK_DISPATCH_PARAM")
public class TaskDispatchParam extends BasePo {

    private String company;

    private String paramId;

    private String paramName;

    private String hostIp;

    private String port;

    private String username;

    private String password;

    private String encoding;

    private String isDynamicPath;

    private String pathRole;

    private String remotePath;

    private String isDynameName;

    private String nameRole;

    private String fileName;

    private String localPath;

    private java.util.Date insertTime;

    private java.util.Date updateTime;

    private String isIgnore;

    private String extend1;

    private String extend2;

    private String extend3;

}
