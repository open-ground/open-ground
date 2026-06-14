package io.github.openground.common.keygen;

import io.github.openground.base.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

/**
 * {@link SequenceProvider} 的默认实现
 * <p>通过 JdbcTemplate + 编程式事务直连数据库 {@code SYS_AUTO_PMKEY} 表获取序列。</p>
 * <p>适用于集成部署模式（业务模块与数据库直连）。</p>
 *
 * @author open-ground
 */
@Slf4j
@RequiredArgsConstructor
public class JdbcSequenceProvider implements SequenceProvider {

    private final DataSourceTransactionManager txManager;

    @Override
    public KeyInfoDomain retrieveAndAdvance(String keyName, int count) {
        DefaultTransactionDefinition td = new DefaultTransactionDefinition();
        td.setPropagationBehavior(DefaultTransactionDefinition.PROPAGATION_REQUIRES_NEW);
        td.setIsolationLevel(TransactionDefinition.ISOLATION_DEFAULT);
        TransactionStatus ts = txManager.getTransaction(td);
        try {
            if (count == 0) {
                count = 1;
            }

            JdbcTemplate jt = new JdbcTemplate(txManager.getDataSource());

            // 推进序列值
            int n = jt.update(
                    "update sys_auto_pmkey set MAX_VALUE = MAX_VALUE+(?*STEP_LEN) where PK_NAME=?",
                    count, keyName);
            if (n != 1) {
                throw new RuntimeException("sys_auto_pmkey 中找到 " + keyName + " 的记录数为:" + n);
            }

            // 查询当前序列信息
            Map<String, Object> row = jt.queryForMap(
                    "select * from sys_auto_pmkey where PK_NAME=?", keyName);
            if (row == null || row.isEmpty()) {
                throw new RuntimeException("sys_auto_pmkey 中不存在: " + keyName);
            }

            KeyInfoDomain keyinfo = new KeyInfoDomain();
            keyinfo.setPkName(keyName);
            keyinfo.setMaxValue(((Number) row.get("MAX_VALUE")).longValue());
            keyinfo.setStepLen(((Number) row.get("STEP_LEN")).intValue());
            keyinfo.setKeyLen(((Number) row.get("PK_LEN")).intValue());
            keyinfo.setSysCd(row.get("SYS_CD") == null ? "" : row.get("SYS_CD").toString());
            keyinfo.setResetDate(row.get("RESET_DATE") == null ? "" : row.get("RESET_DATE").toString());
            keyinfo.setResetFreq(row.get("RESET_FREQ") == null ? "" : row.get("RESET_FREQ").toString());
            keyinfo.setRemark(row.get("REMARK") == null ? "" : row.get("REMARK").toString());
            keyinfo.setPrefix(row.get("PREFIX") == null ? "" : row.get("PREFIX").toString());
            keyinfo.setNextKey(keyinfo.getMaxValue() - keyinfo.getStepLen() + 1);

            // 主键重置逻辑
            handleReset(jt, keyinfo, count);

            txManager.commit(ts);
            return keyinfo;
        } catch (RuntimeException e) {
            log.error("获取序列异常: {}", e.getMessage());
            txManager.rollback(ts);
            throw e;
        } catch (Exception e) {
            log.error("获取序列异常: {}", e.getMessage());
            txManager.rollback(ts);
            throw new RuntimeException("获取序列异常", e);
        }
    }

    /**
     * 处理主键重置逻辑
     */
    private void handleReset(JdbcTemplate jt, KeyInfoDomain keyinfo, int count) {
        String resetFreq = keyinfo.getResetFreq();
        if (resetFreq == null || resetFreq.isEmpty()) {
            return;
        }

        String now;
        String sysCd = keyinfo.getSysCd();
        if (sysCd == null || sysCd.isEmpty()) {
            now = DateUtils.getDatetime("yyyyMMdd");
        } else {
            Map<?, ?> sys = jt.queryForMap(
                    "SELECT sys_run_date FROM sys_run_info WHERE sys_cd=?", sysCd);
            Object sysdate = sys.get("sys_run_date");
            if (sysdate == null || sysdate.toString().isEmpty()) {
                sysdate = new SimpleDateFormat("yyyyMMdd").format(new Date());
            }
            now = sysdate.toString();
        }

        String last = keyinfo.getResetDate();
        String reset;
        if (last == null || last.isEmpty()) {
            // 首次重置
            reset = now;
        } else {
            // 非首次重置：判断是否满足重置条件
            String freqDay = DateUtils.getDateAfter(last, "yyyyMMdd", 1, resetFreq);
            if (now.compareTo(freqDay) >= 0) {
                log.info("满足重置 keyinfo[{}] 条件", keyinfo.getPkName());
                reset = now;
            } else {
                log.info("不满足重置 keyinfo[{}] 条件", keyinfo.getPkName());
                return;
            }
        }

        log.debug("重置 keyinfo: {}", keyinfo);
        jt.update(
                "update sys_auto_pmkey set max_value = 1+(?*STEP_LEN), reset_date = ? where pk_name=?",
                count, reset, keyinfo.getPkName());
        keyinfo.setNextKey(1);
        keyinfo.setMaxValue(1 + count * keyinfo.getStepLen());
        keyinfo.setResetDate(reset);
    }
}
