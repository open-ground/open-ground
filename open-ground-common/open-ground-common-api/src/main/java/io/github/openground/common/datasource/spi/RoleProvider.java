package io.github.openground.common.datasource.spi;

import io.github.openground.common.datasource.dto.RoleInfo;

import java.util.List;

/**
 * 角色提供者 SPI 接口
 *
 * <p>业务系统实现此接口并声明为 Spring Bean，向数据源模块提供全部角色列表，
 * 用于数据源表权限管理的角色选择。</p>
 *
 * <h3>自定义实现</h3>
 * <pre>{@code
 * @Component
 * public class MyRoleProvider implements RoleProvider {
 *     public List<RoleInfo> getAllRoles() {
 *         // 从数据库或接口查询角色列表
 *     }
 * }
 * }</pre>
 *
 * @author open-ground
 * @since 1.0.4
 */
@FunctionalInterface
public interface RoleProvider {

    /**
     * 获取全部角色列表
     *
     * @return 角色列表，不可返回 null
     */
    List<RoleInfo> getAllRoles();
}
