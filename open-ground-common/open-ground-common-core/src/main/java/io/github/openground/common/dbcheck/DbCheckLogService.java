package io.github.openground.common.dbcheck;


import com.github.pagehelper.PageInfo;

/**
 * DbCheck 操作日志服务接口
 *
 * @author ground-auth
 */
public interface DbCheckLogService {

    /**
     * 保存操作日志
     *
     * @param log 日志实体
     */
    void save(DbCheckLogDO log);

    /**
     * 分页查询日志列表
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageInfo<DbCheckLogDO> pageList(DbCheckLogDO query);
}
