package io.github.openground.common.dbcheck.dao;

import io.github.openground.common.dbcheck.model.DbCheckLogDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * DbCheck 操作日志数据访问接口
 *
 * @author ground-auth
 */
@Mapper
public interface DbCheckLogDao {

    /**
     * 新增日志
     *
     * @param log 日志实体
     * @return 影响行数
     */
    int insert(DbCheckLogDO log);

    /**
     * 分页查询日志列表
     *
     * @param query 查询条件
     * @return 日志列表
     */
    List<DbCheckLogDO> selectList(DbCheckLogDO query);

    /**
     * 根据 ID 查询
     *
     * @param id 主键
     * @return 日志实体
     */
    DbCheckLogDO selectById(@Param("id") String id);

    /**
     * 查询总记录数
     *
     * @param query 查询条件
     * @return 记录数
     */
    int count(DbCheckLogDO query);
}
