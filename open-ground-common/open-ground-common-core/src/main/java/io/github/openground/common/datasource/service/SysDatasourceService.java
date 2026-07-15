package io.github.openground.common.datasource.service;

import com.github.pagehelper.PageInfo;
import io.github.openground.common.datasource.entity.SysDatasourceDO;

import java.sql.Connection;
import java.util.List;

/**
 * 系统数据源 Service 接口
 *
 * @author open-ground
 * @since 1.0.2
 */
public interface SysDatasourceService {

    /**
     * 创建数据源
     */
    SysDatasourceDO create(SysDatasourceDO ds);

    /**
     * 更新数据源
     */
    SysDatasourceDO update(SysDatasourceDO ds);

    /**
     * 删除数据源（逻辑删除）
     */
    void delete(Long id);

    /**
     * 根据 ID 获取数据源
     */
    SysDatasourceDO getById(Long id);

    /**
     * 分页查询数据源列表
     */
    PageInfo<SysDatasourceDO> list(SysDatasourceDO query);

    /**
     * 测试数据源连接
     *
     * @param ds                数据源信息
     * @param passwordEncrypted 密码是否已加密（true=密文需解密，false=明文直接使用）
     */
    boolean testConnection(SysDatasourceDO ds, boolean passwordEncrypted);

    /**
     * 获取数据源 JDBC 连接
     */
    Connection getConnection(SysDatasourceDO ds);

    /**
     * 查询数据源表列表（受表权限控制）
     *
     * <p>严格模式：未登录用户或无权限配置均返回空列表。</p>
     */
    List<String> listTables(Long datasourceId);

    /**
     * 查询数据源全部表列表（跳过权限过滤，供管理界面使用）
     */
    List<String> listAllTables(Long datasourceId);

    /**
     * 查询表字段列表
     */
    List<java.util.Map<String, Object>> listTableColumns(Long datasourceId, String tableName);

    /**
     * 获取所有有效数据源（供 SysDatasourceProvider 使用）
     */
    List<SysDatasourceDO> listAll();
}
