package io.github.openground.land.job;

import cn.hutool.core.util.StrUtil;
import io.github.openground.base.utils.CommonUtil;
import io.github.openground.land.api.domain.JobOut;
import io.github.openground.land.api.job.JobEngine;
import io.github.openground.land.common.entity.TaskDispatchExeLogDomain;
import io.github.openground.land.common.util.TaskDateUtil;
import io.github.openground.land.core.TaskDispatchServiceUtil;
import io.github.openground.land.mapper.TaskDispatchActiveHostMapper;
import io.github.openground.land.mapper.TaskDispatchExeLogMapper;
import io.github.openground.land.service.ActiveHostService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 日切作业 — 批处理结束，将系统跑批日期切换到下一个工作日
 * <p>
 * 参数：isAutoExe（是否自动执行）、sysEodDate（手动执行时的目标日期）、
 * taskPlanId（执行计划ID）、cpsGroup（目标调度组）
 * </p>
 *
 * @author jack.zhang
 * @since 2026-06-25
 */
@Slf4j
@Service
@Transactional
public class EndJob extends JobEngine {

    @Autowired
    private TaskDispatchExeLogMapper exeLogMapper;

    @Autowired
    private TaskDispatchActiveHostMapper activeHostMapper;

    @Autowired
    private ActiveHostService activeHostService;

    @Override
    public JobOut execute(Map<String, Object> param) throws Exception {
        JobOut out = new JobOut();
        String curCpsGroup = TaskDispatchServiceUtil.getCpsGroup();

        String sysEodDate = TaskDateUtil.getSysEodDate(curCpsGroup);
        Date nextday = TaskDateUtil.tomorrow(CommonUtil.getString2Date(sysEodDate, CommonUtil.yyyyMMdd));
        String nextEodDate = TaskDateUtil.formatDate(nextday);

        if (param.containsKey("isAutoExe")) {
            Object se = param.get("sysEodDate");
            nextEodDate = se != null ? se.toString() : nextEodDate;
        }

        String taskPlanId = CommonUtil.getStringValueFromHashMap(param, "taskPlanId");
        if (StrUtil.isNotBlank(taskPlanId)) {
            TaskDispatchExeLogDomain taskPlan = exeLogMapper.findExeLogById(taskPlanId);
            if (taskPlan != null && taskPlan.getErrInfo() != null && taskPlan.getErrInfo().contains("成功")) {
                out.setSuccess(true);
                out.setMessage("当日已切日成功");
                return out;
            }
        }

        String str = CommonUtil.getStringValueFromHashMap(param, "cpsGroup");
        List<String> cpsGroups = new ArrayList<>();
        if (StrUtil.isNotBlank(str)) {
            cpsGroups = Arrays.asList(str.split(","));
        } else {
            cpsGroups.add(curCpsGroup);
        }
        activeHostService.updateSysEodDate(cpsGroups, null, nextEodDate);

        out.setSuccess(true);
        out.setMessage("日切执行成功,跑批日期切日到：" + nextEodDate);
        return out;
    }

}
