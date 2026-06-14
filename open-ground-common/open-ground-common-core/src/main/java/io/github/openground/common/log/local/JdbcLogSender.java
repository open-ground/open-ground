package io.github.openground.common.log.local;

import io.github.openground.common.keygen.IdGenerator;
import io.github.openground.common.log.domain.SysOptLog;
import io.github.openground.common.log.service.LogSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 默认操作日志发送器（集成部署模式）
 * <p>通过 JdbcTemplate 直接将操作日志写入 {@code sys_opt_log} 表。</p>
 * <p>适用于集成部署模式（flow/dmp 等模块与 auth-core 在同一应用内）。</p>
 *
 * @author open-ground
 */
@Slf4j
@RequiredArgsConstructor
public class JdbcLogSender implements LogSender {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void send(SysOptLog optLog) {
        if (optLog == null) {
            return;
        }
        try {
            // 生成日志ID
            if (optLog.getLogId() == null || optLog.getLogId().isEmpty()) {
                optLog.setLogId(IdGenerator.generateId() + "");
            }

            jdbcTemplate.update(
                    "insert into sys_opt_log(log_id, opt_type, opt_url, opt_remark, opt_method, " +
                    "opt_param, user_id, ip_address, opt_status, err_msg, sys_time) " +
                    "values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    optLog.getLogId(),
                    optLog.getOptType(),
                    optLog.getOptUrl(),
                    optLog.getOptRemark(),
                    optLog.getOptMethod(),
                    optLog.getOptParam(),
                    optLog.getUserId(),
                    optLog.getIpAddress(),
                    optLog.getOptStatus(),
                    optLog.getErrMsg(),
                    optLog.getSysTime()
            );
            log.debug("操作日志已保存: logId={}, type={}", optLog.getLogId(), optLog.getOptType());
        } catch (Exception e) {
            log.warn("操作日志写入失败: logId={}, type={}", optLog.getLogId(), optLog.getOptType(), e);
        }
    }
}
