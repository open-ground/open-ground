package io.github.openground.land.job;

import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.core.util.StrUtil;
import io.github.openground.base.utils.CommonUtil;
import io.github.openground.common.jdbc.DynamicDataSourceManager;
import io.github.openground.common.jdbc.DynamicJdbcTemplate;
import io.github.openground.land.api.domain.JobOut;
import io.github.openground.land.api.job.JobEngine;
import io.github.openground.land.mapper.TaskDispatchConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 存储过程调用任务
 * <p>
 * 参数：procName（过程名，必填）、sysEodDate（跑批日期）、dsName（数据源名，可选）
 * </p>
 *
 * @author jack.zhang
 * @since 2026-06-25
 */
@Slf4j
@Service
public class ProcedurelTask extends JobEngine {

    @Autowired
    private TaskDispatchConfigMapper configMapper;

    @Autowired
    private DynamicJdbcTemplate dynamicJdbcTemplate;

    @Autowired
    private DynamicDataSourceManager dynamicDataSourceManager;

    @Override
    public JobOut execute(Map<String, Object> param) throws Exception {
        JobOut out = new JobOut();
        Date start = new Date();
        log.info("任务执行start");
        String retCode = "";
        String retMsg = "";
        String procName = CommonUtil.getStringValueFromHashMap(param, "procName");
        try {
            String eodDate = CommonUtil.getStringValueFromHashMap(param, "sysEodDate");
            if (StrUtil.isBlank(procName)) {
                throw new IllegalArgumentException("procName 参数不能为空");
            }
            if (param.containsKey("dsName")) {
                String dsName = CommonUtil.getStringValueFromHashMap(param, "dsName");
                String dbType = dynamicDataSourceManager.getDbType(dsName);
                String sql = DynamicJdbcTemplate.getProcSql(dbType, procName, eodDate);
                Map<String, Object> result = dynamicJdbcTemplate.execProcedure(dsName, sql);
                retCode = (String) result.get("retCode");
                retMsg = (String) result.get("retMsg");
            } else {
                Map<String, Object> queryMap = new HashMap<>();
                queryMap.put("procName", procName);
                queryMap.put("eodDate", eodDate);
                configMapper.callProcedure(queryMap);
                retCode = (String) queryMap.get("retCode");
                retMsg = (String) queryMap.get("retMsg");
            }
            if ("0000".equals(retCode) || "0".equals(retCode) || "S".equals(retCode)) {
                out.setSuccess(true);
            } else {
                out.setSuccess(false);
            }
            out.setMessage("存储过程[" + procName + "]执行完成: retCode=" + retCode + ", retMsg=" + retMsg);
            log.info("任务执行成功，耗时[{}]毫秒", new Date().getTime() - start.getTime());
        } catch (Exception e) {
            log.error("任务执行失败：", e);
            out.setSuccess(false);
            String message = DynamicJdbcTemplate.getMessage(ExceptionUtil.stacktraceToString(e), procName);
            out.setMessage("存储过程[" + procName + "]执行失败:" + message + "\n" + ExceptionUtil.stacktraceToString(e, 1500));
        }
        return out;
    }

}
