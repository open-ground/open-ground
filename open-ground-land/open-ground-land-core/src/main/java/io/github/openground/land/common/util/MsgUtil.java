package io.github.openground.land.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * <p>Title: MsgUtil</p>
 * <p>Description: 生成消息工具类</P>
 *
 * @Author:jack.zhang
 * @Date 2021/8/3 16:31
 * @Version 3.0.0
 */
@Slf4j
@Component("msgUtil")
public class MsgUtil {

//    private static MsgUtilComponent msgUtilComponent;
//
//    @Autowired
//    public void setMsgUtilComponent(MsgUtilComponent msgUtilComponent) {
//        MsgUtil.msgUtilComponent = msgUtilComponent;
//    }

    /**
     * <p>Description: 生成定时任务异常通知</P>
     *
     * @Author:jack.zhang
     * @Version 3.0.0
     * @Date 2021/8/3 16:33
     * @param jobName 任务名称
     * @param errMsg 错误信息
     * @return void
    */
    public static void createErrMsg(String jobName, String errMsg) throws Exception {
//        msgUtilComponent.createErrMsg(jobName, errMsg);
    }

    /**
     * <p>Description: 生成定时任务通知</P>
     *
     * @Author:jack.zhang
     * @Version 3.0.0
     * @Date 2021/8/3 16:33
     * @param jobName 任务名称
     * @param message 通知信息
     * @return void
     */
    public static void createMsg(String jobName, String message) throws Exception {
//        msgUtilComponent.createMsg(jobName, message);
    }
}
