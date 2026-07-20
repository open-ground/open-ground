package io.github.openground.land.service;

import io.github.openground.base.utils.CommonUtil;
import io.github.openground.base.utils.MapUtil;
import io.github.openground.land.common.util.TaskDateUtil;
import io.github.openground.land.config.TaskConfig;
import io.github.openground.land.mapper.TaskDispatchActiveHostMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务服务注册发现服务
 * <p>统一管理 TASK_DISPATCH_ACTIVE_HOST 表的心跳注册、服务发现、活跃判定、过期清理</p>
 *
 * @author jack.zhang
 * @since 2026-07-17
 */
@Slf4j
@Service
public class ActiveHostService {

    @Autowired
    private TaskDispatchActiveHostMapper activeHostMapper;

    @Autowired
    private TaskConfig taskConfig;

    // ==================== 注册与续约 ====================


    /**
     * 注册或续约心跳（含跑批日期）
     */
    public void heartbeat(String cpsGroup, String hostIp) {
        Map<String, Object> param = new HashMap<>();
        param.put("hostIp", hostIp);
        param.put("activeTime", TaskDateUtil.getMachingCurrentTime());
        param.put("cpsGroup", cpsGroup);
        int count = activeHostMapper.updateActiveHost(param);
        if (count == 0) {
            // 新主机注册：默认 OFF，设跑批日期
            param.put("activeStatus", "OFF");
            param.put("sysEodDate", CommonUtil.getCurrDate(CommonUtil.yyyyMMdd));
            activeHostMapper.insertActiveHost(param);
        }
    }

    /**
     * 取消注册（标记为 OFF）
     */
    public void unregister(String cpsGroup, String hostIp) {
        Map<String, Object> param = new HashMap<>();
        param.put("hostIp", hostIp);
        param.put("cpsGroup", cpsGroup);
        param.put("activeTime", new Date());
        param.put("activeStatus", "OFF");
        activeHostMapper.updateActiveHost(param);
    }

    /**
     * 获取心跳阈值时间
     */
    private Date getActiveTimeThreshold() {
        return new Date(System.currentTimeMillis() - taskConfig.getActiveHostTimeoutSeconds() * 1000L);
    }

    /**
     * 获取过期清理阈值时间
     */
    private Date getCleanupThreshold() {
        return new Date(System.currentTimeMillis() - taskConfig.getActiveHostCleanupDays() * 86400L * 1000L);
    }

    // ==================== 服务发现 ====================

    /**
     * 获取活跃主机列表（带判活阈值过滤）
     *
     * @param cpsGroup 调度组，null 或空时不限制
     * @param hostIp   主机 IP，null 或空时不限制
     * @return 主机列表（Map key 已转大写）
     */
    public List<Map<String, Object>> getActiveHosts(String cpsGroup, String hostIp) {
        Map<String, Object> param = new HashMap<>();
        param.put("cpsGroup", cpsGroup);
        param.put("hostIp", hostIp);
        param.put("activeTimeThreshold", getActiveTimeThreshold());
        return queryAndConvertKeys(param);
    }

    /**
     * 获取所有主机列表（不过滤活跃状态）
     */
    public List<Map<String, Object>> getAllHosts(String cpsGroup) {
        Map<String, Object> param = new HashMap<>();
        param.put("cpsGroup", cpsGroup);
        return queryAndConvertKeys(param);
    }

    /**
     * 查询并统一转大写 key
     */
    private List<Map<String, Object>> queryAndConvertKeys(Map<String, Object> param) {
        List<Map<String, Object>> list = activeHostMapper.hostListlistPage(param);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> map : list) {
            result.add(MapUtil.mapKeyUpperCase(map));
        }
        return result;
    }

    /**
     * 获取指定 cpsGroup 下可用的实例 IP 列表（判活阈值内）
     */
    public List<String> getAvailableHostIps(String cpsGroup) {
        Map<String, Object> param = new HashMap<>();
        param.put("cpsGroup", cpsGroup);
        param.put("activeTimeThreshold", getActiveTimeThreshold());
        return activeHostMapper.selectActiveHostsByCpsGroup(param);
    }

    /**
     * 查询所有活跃的 cpsGroup（去重）
     */
    public List<String> getActiveCpsGroups() {
        Map<String, Object> param = new HashMap<>();
        param.put("activeTimeThreshold", getActiveTimeThreshold());
        return activeHostMapper.selectDistinctActiveCpsGroups(param);
    }

    /**
     * 获取不活跃的主机 IP 列表（超时无心跳）
     */
    public List<String> getInactiveHostIps(String cpsGroup) {
        Map<String, Object> param = new HashMap<>();
        param.put("cpsGroup", cpsGroup);
        param.put("activeTime", getActiveTimeThreshold());
        return activeHostMapper.selectHostIpByActiveTime(param);
    }

    /**
     * 获取指定任务可用的服务主机 IP
     *
     * @param cpsGroup      调度组
     * @param taskId        任务 ID
     * @param excludeHostIp 排除的主机 IP
     */
    public List<String> getServiceHostByTask(String cpsGroup, String taskId, String excludeHostIp) {
        Map<String, Object> param = new HashMap<>();
        param.put("cpsGroup", cpsGroup);
        param.put("activeTime", getActiveTimeThreshold());
        param.put("exeingTaskId", taskId);
        param.put("exeingHostIp", excludeHostIp);
        return activeHostMapper.selectServiceHostIpByTask(param);
    }

    /**
     * 获取主机状态
     *
     * @return ACTIVE_TIME / ACTIVE_STATUS / CPS_GROUP / HOST_IP / SYS_EOD_DATE
     */
    public Map<String, Object> getHostStatus(String hostIp, String cpsGroup) {
        Map<String, Object> param = new HashMap<>();
        param.put("hostIp", hostIp);
        param.put("cpsGroup", cpsGroup);
        Map<String, Object> result = activeHostMapper.selectActiveHostStatus(param);
        if (result != null) {
            result = MapUtil.mapKeyUpperCase(result);
        }
        return result;
    }

    /**
     * 更新系统跑批日期
     */
    public void updateSysEodDate(List<String> cpsGroups, String hostIp, String sysEodDate) {
        Map<String, Object> param = new HashMap<>();
        param.put("cpsGroups", cpsGroups);
        param.put("hostIp", hostIp);
        param.put("sysEodDate", sysEodDate);
        activeHostMapper.updateSysEodDate(param);
    }

    // ==================== 定时清理 ====================

    /**
     * 清理超过保留天数无更新的主机记录（每天凌晨 3 点执行）
     *
     * @return 清理记录数
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public int cleanupExpiredHosts() {
        int days = taskConfig.getActiveHostCleanupDays();
        Date threshold = getCleanupThreshold();
        log.info("开始清理超过 {} 天未更新的主机记录，阈值时间: {}", days, threshold);

        // 由于使用逻辑删除，将 del_flag 置为 '1'
        // 实际可改为 DELETE，取决于业务要求
        Map<String, Object> param = new HashMap<>();
        param.put("cleanupThreshold", threshold);
        int count = activeHostMapper.deleteExpiredHosts(param);
        log.info("清理过期主机记录完成，共清理 {} 条", count);
        return count;
    }
}
