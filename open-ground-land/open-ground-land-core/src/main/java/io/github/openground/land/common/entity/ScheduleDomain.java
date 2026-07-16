package io.github.openground.land.common.entity;

import lombok.Data;

/**
 * 调度统计实体
 *
 * @author jack.zhang
 * @since 2026-07-16
 */
@Data
public class ScheduleDomain {

    /** 调度总次数 */
    private int schCount;

    /** 调度成功次数 */
    private int schCountSuccess;

    /** 调度失败次数 */
    private int schCountError;

    /** 跑批日期 */
    private String sysEodDate;
}
