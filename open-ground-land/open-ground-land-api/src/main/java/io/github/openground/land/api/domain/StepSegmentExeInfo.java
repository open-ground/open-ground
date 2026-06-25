package io.github.openground.land.api.domain;

import lombok.Data;

import java.io.Serializable;

/**
 * <p>Title: StepSegmentExeInfo</p>
 * <p>Description: </P>
 *
 * @Author:jack.zhang
 * @Date 2022/9/23 15:33
 */
@Data
public class StepSegmentExeInfo implements Serializable {

    public static final String STATUS_SUCCESS = "SUCCESS";

    public static final String STATUS_FAIL = "FAIL";

    private static final long serialVersionUID = 1L;

    private String segment;

    private String taskPlanId;

    private String stepId;

    private String exeUrl;

    private String threadName;

    private String infoMessage;

    private String exceptionMessage;

    private String stepStatus;

    private boolean finished;
}
