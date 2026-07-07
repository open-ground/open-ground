package io.github.openground.common.jdbc;

import org.apache.ibatis.session.SqlSession;

/**
 * SqlSession 回调接口
 *
 * <p>用于在指定动态数据源上执行 MyBatis 操作，调用方通过 {@link SqlSession}
 * 自行获取 Mapper 并执行 SQL。
 *
 * @param <T> 返回类型
 * @author open-ground
 * @since 1.0.2
 */
@FunctionalInterface
public interface SqlSessionCallback<T> {

    /**
     * 在 SqlSession 中执行操作
     *
     * @param session MyBatis SqlSession
     * @return 执行结果
     */
    T doInSqlSession(SqlSession session);
}
