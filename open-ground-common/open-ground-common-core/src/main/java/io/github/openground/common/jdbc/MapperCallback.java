package io.github.openground.common.jdbc;

/**
 * Mapper 回调接口
 *
 * <p>用于在指定动态数据源上执行 Mapper 操作，调用方获取 Mapper 代理后执行方法。
 * 相比 {@link SqlSessionCallback}，此接口更简洁，调用方无需了解 SqlSession API。
 *
 * @param <M> Mapper 类型
 * @param <T> 返回类型
 * @author open-ground
 * @since 1.0.2
 */
@FunctionalInterface
public interface MapperCallback<M, T> {

    /**
     * 使用 Mapper 代理执行操作
     *
     * @param mapper Mapper 代理
     * @return 执行结果
     */
    T doWithMapper(M mapper);
}
