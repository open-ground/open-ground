package io.github.openground.land.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 主机活动表 Mapper（TASK_DISPATCH_ACTIVE_HOST）
 *
 * @author jack.zhang
 * @since 2026-06-24
 */
@Mapper
public interface TaskDispatchActiveHostMapper {

    int updateActiveHost(Map<String, Object> param);

    Map<String, Object> selectActiveHostStatus(Map<String, Object> param);

    List<Map<String, Object>> hostListlistPage(Map<String, Object> param);

    int insertActiveHost(Map<String, Object> param);

    List<String> selectHostIpByActiveTime(Map<String, Object> param);

    List<String> selectServiceHostIpByTask(Map<String, Object> param);

    /** 获取指定 cpsGroup 下所有状态为 ON 的活跃主机 IP */
    List<String> selectActiveHostsByCpsGroup(Map<String, Object> param);

    /** 查询所有活跃的 cpsGroup（去重）— 活跃判定：ACTIVE_STATUS='ON' 或 ACTIVE_TIME > 阈值 */
    List<String> selectDistinctActiveCpsGroups(Map<String, Object> param);

    int updateHostIp(Map<String, Object> param);

    int updateSysEodDate(Map<String, Object> param);

    /** 清理超过阈值的过期主机记录 */
    int deleteExpiredHosts(Map<String, Object> param);
}
