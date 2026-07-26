package io.github.openground.common.datasource.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import io.github.openground.base.exception.CommonException;
import io.github.openground.base.utils.AESUtil;
import io.github.openground.common.datasource.entity.SysDatasourceDO;
import io.github.openground.common.datasource.mapper.SysDatasourceMapper;
import io.github.openground.common.datasource.service.SysDatasourceService;
import io.github.openground.common.datasource.service.SysDatasourceTablePermissionService;
import io.github.openground.common.datasource.service.TableMetadataService;
import io.github.openground.common.keygen.KeyGenerator;
import io.github.openground.common.security.SecurityContextHolder;
import io.github.openground.common.security.spi.UserDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;

/**
 * 系统数据源 Service 实现
 *
 * <p>从 DmpDatasourceServiceImpl 迁移，密码加解密改用 open-ground AESUtil。
 *
 * @author open-ground
 * @since 1.0.2
 */
@Slf4j
@Service
public class SysDatasourceServiceImpl implements SysDatasourceService {

    @Autowired
    private SysDatasourceMapper datasourceMapper;

    @Autowired(required = false)
    private SysDatasourceTablePermissionService tablePermissionService;

    @Autowired
    private TableMetadataService tableMetadataService;

    @Value("${ground.datasource.encrypt.key:ABCDEFG123456KEY}")
    private String encryptKey;

    @Value("${ground.datasource.encrypt.iv:ABCDEFG1234567IV}")
    private String encryptIv;

    @Override
    public SysDatasourceDO create(SysDatasourceDO ds) {
        ds.setId(KeyGenerator.getInternalKey());
        ds.setPassword(encrypt(ds.getPassword()));
        ds.setDelFlag("0");
        ds.setCreateTime(new Date());
        ds.setUpdateTime(new Date());
        datasourceMapper.insert(ds);
        return ds;
    }

    @Override
    public SysDatasourceDO update(SysDatasourceDO ds) {
        SysDatasourceDO existing = datasourceMapper.selectById(ds.getId());
        if (existing == null) {
            throw new CommonException("514003", "数据源不存在");
        }
        if (ds.getPassword() != null && !ds.getPassword().isEmpty()) {
            ds.setPassword(encrypt(ds.getPassword()));
        } else {
            // 密码为空时不更新，避免覆盖原密码
            ds.setPassword(null);
        }
        ds.setUpdateTime(new Date());
        datasourceMapper.updateById(ds);
        return datasourceMapper.selectById(ds.getId());
    }

    @Override
    public void delete(Long id) {
        datasourceMapper.deleteById(id);
    }

    @Override
    public SysDatasourceDO getById(Long id) {
        SysDatasourceDO ds = datasourceMapper.selectById(id);
        if (ds != null && ds.getPassword() != null) {
            ds.setPassword(decrypt(ds.getPassword()));
        }
        return ds;
    }

    @Override
    public PageInfo<SysDatasourceDO> list(SysDatasourceDO query) {
        int pageNum = query.getPageNum() != null ? query.getPageNum() : 1;
        int pageSize = query.getPageSize() != null ? query.getPageSize() : 10;
        PageHelper.startPage(pageNum, pageSize);
        return new PageInfo<>(datasourceMapper.selectList(query));
    }


    @Override
    public List<SysDatasourceDO> listAll(SysDatasourceDO query) {
        List<SysDatasourceDO> list = datasourceMapper.selectList(query);
        for (SysDatasourceDO ds : list) {
            ds.setPassword(null);
        }
        return list;
    }

    @Override
    public boolean testConnection(SysDatasourceDO ds, boolean passwordEncrypted) {
        // passwordEncrypted=true：密码来自数据库，是密文，需解密
        // passwordEncrypted=false：密码来自编辑界面，是明文，直接使用
        String password = passwordEncrypted ? decrypt(ds.getPassword()) : ds.getPassword();
        try {
            return tryConnect(ds, password);
        } catch (Exception e) {
            throw new CommonException("518006", "数据源连接测试失败：" + e.getMessage());
        }
    }

    /**
     * 使用指定密码尝试建立连接并检查有效性
     */
    private boolean tryConnect(SysDatasourceDO ds, String password) throws SQLException {
        String url = resolveJdbcUrl(ds);
        String driverClass = ds.getDriverClassName();
        if (driverClass != null && !driverClass.isEmpty()) {
            try {
                Class.forName(driverClass);
            } catch (ClassNotFoundException e) {
                log.warn("加载驱动类失败: {}, 尝试自动发现", driverClass);
            }
        }
        Properties props = new Properties();
        props.setProperty("user", ds.getUsername());
        props.setProperty("password", password);
        props.setProperty("connectTimeout", "5000");
        props.setProperty("socketTimeout", "10000");
        try (Connection conn = DriverManager.getConnection(url, props)) {
            return conn.isValid(5);
        }
    }

    @Override
    public Connection getConnection(SysDatasourceDO ds) {
        try {
            String url = resolveJdbcUrl(ds);
            String driverClass = ds.getDriverClassName();
            if (driverClass != null && !driverClass.isEmpty()) {
                try {
                    Class.forName(driverClass);
                } catch (ClassNotFoundException e) {
                    log.warn("加载驱动类失败: {}, 尝试自动发现", driverClass);
                }
            }
            Properties props = new Properties();
            props.setProperty("user", ds.getUsername());
            props.setProperty("password", ds.getPassword());
            props.setProperty("connectTimeout", "5000");
            props.setProperty("socketTimeout", "10000");
            return DriverManager.getConnection(url, props);
        } catch (Exception e) {
            throw new CommonException("518005", "获取数据库连接失败: " + e.getMessage());
        }
    }

    @Override
    public List<Map<String, Object>> listTables(Long datasourceId) {
        // 严格模式：未获取到当前用户，不返回任何表
        UserDetails user = SecurityContextHolder.getCurrentUser();
        if (user == null || user.getRoleIds() == null || user.getRoleIds().isEmpty()) {
            log.debug("table permission: no user context or no roles, returning empty table list");
            return Collections.emptyList();
        }

        // 获取 JDBC 原始表列表（含表名+注释）
        List<Map<String, Object>> allTables = doListTables(datasourceId);

        // 无表时直接返回
        if (allTables.isEmpty()) {
            return allTables;
        }

        // 权限过滤：查询当前用户角色可见的表
        if (tablePermissionService != null) {
            Set<String> allowed = tablePermissionService.queryAllowedTablesByLongRoles(
                    datasourceId, new HashSet<>(user.getRoleIds()));
            if (allowed == null) {
                // null 表示数据源无权限配置 → 严格模式：不返回任何表
                log.debug("table permission: no permission config for datasource {}, returning empty", datasourceId);
                return Collections.emptyList();
            }
            allTables.removeIf(t -> !allowed.contains(t.get("tableName")));
        } else {
            // 无表权限服务 → 不返回任何表（严格模式）
            log.debug("table permission: no tablePermissionService available, returning empty");
            return Collections.emptyList();
        }

        return allTables;
    }

    @Override
    public List<Map<String, Object>> listAllTables(Long datasourceId) {
        return doListTables(datasourceId);
    }

    /**
     * 通过 DynamicJdbcTemplate + DbDialect 获取表列表（跳过权限过滤，多数据库兼容）
     */
    private List<Map<String, Object>> doListTables(Long datasourceId) {
        SysDatasourceDO ds = datasourceMapper.selectById(datasourceId);
        if (ds == null) {
            throw new CommonException("514003", "数据源不存在");
        }
        try {
            return tableMetadataService.getTableList(ds.getDsName());
        } catch (Exception e) {
            throw new CommonException("518005", "读取表列表失败: " + e.getMessage());
        }
    }

    @Override
    public List<Map<String, Object>> listTableColumns(Long datasourceId, String tableName) {
        SysDatasourceDO ds = datasourceMapper.selectById(datasourceId);
        if (ds == null) {
            throw new CommonException("514003", "数据源不存在");
        }
        try {
            return  tableMetadataService.getTableColumns(ds.getDsName(), tableName);
        } catch (Exception e) {
            throw new CommonException("518005", "读取表字段失败: " + e.getMessage());
        }
    }

    @Override
    public List<SysDatasourceDO> listAll() {
        return datasourceMapper.selectList(new SysDatasourceDO());
    }

    // ====== 内部方法 ======

    private String encrypt(String plainText) {
        try {
            return AESUtil.encrypt(encryptKey, encryptIv, plainText);
        } catch (Exception e) {
            throw new CommonException("518005", "密码加密失败");
        }
    }

    private String decrypt(String encrypted) {
        return AESUtil.decrypt(encryptKey, encryptIv, encrypted);
    }

    private String resolveJdbcUrl(SysDatasourceDO ds) {
        if (ds.getJdbcUrl() != null && !ds.getJdbcUrl().isEmpty()) {
            return ds.getJdbcUrl();
        }
        // 兼容旧数据：host/port/databaseName 拼接
        StringBuilder sb = new StringBuilder("jdbc:");
        sb.append(ds.getDbType() != null ? ds.getDbType() : "mysql");
        sb.append("://").append(ds.getHost() != null ? ds.getHost() : "localhost");
        if (ds.getPort() != null) {
            sb.append(":").append(ds.getPort());
        }
        sb.append("/").append(ds.getDatabaseName() != null ? ds.getDatabaseName() : "");
        return sb.toString();
    }

}
