package io.github.openground.land.api.domain;

import lombok.Data;

import java.util.Map;

/**
 * <p>Title: JobOut</p>
 * <p>Description: </P>
 *
 * @Author:jack.zhang
 * @Date 2020/7/22 13:44
 * @Version 3.0.0
 */
@Data
public class JobOut{

    //@V(desc = "任务执行成功标志")
    private Boolean success;

    //@V(desc = "任务执行信息")
    private String message;

    private Map<String, Object> resultmap;

}
