package io.github.openground.common.dbcheck.service;

import io.github.openground.common.datasource.entity.SysDatasourceDO;
import io.github.openground.common.datasource.service.SysDatasourceService;
import io.github.openground.common.dbcheck.spi.DbCheckDatasourceProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 系统数据源 DbCheck 适配器
 *
 * <p>将 SYS_DATASOURCE 表中配置的数据源暴露给 DbCheck 模块，
 * 使其能够执行表结构/数据/注释检查。
 *
 * <p>替代原 DmpDbCheckDatasourceProvider，引用 SysDatasourceService。
 *
 * @author ground-auth
 * @since 1.0.2
 */
@Slf4j
public class DbCheckSysDatasourceProvider implements DbCheckDatasourceProvider {

    @Autowired
    private SysDatasourceService sysDatasourceService;

    @Override
    public List<DatasourceInfo> listDatasources() {
        if (sysDatasourceService == null) {
            log.debug("SysDatasourceService not available, returning empty list");
            return Collections.emptyList();
        }

        try {
            List<SysDatasourceDO> list = sysDatasourceService.listAll();
            if (list == null || list.isEmpty()) {
                return Collections.emptyList();
            }

            List<DatasourceInfo> result = new ArrayList<>(list.size());
            for (SysDatasourceDO ds : list) {
                DatasourceInfo info = new DatasourceInfo();
                info.setId(ds.getId());
                info.setName(ds.getDsName());
                info.setDbType(ds.getDbType() != null ? ds.getDbType() : "mysql");
                info.setSource("sys_datasource");
                result.add(info);
            }
            log.info("系统数据源列表已加载: {} 个", list.size());
            return result;
        } catch (Exception e) {
            log.error("获取系统数据源列表失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public Connection getConnection(Long datasourceId) {
        if (sysDatasourceService == null) {
            throw new IllegalStateException("SysDatasourceService 不可用");
        }
        SysDatasourceDO ds = sysDatasourceService.getById(datasourceId);
        if (ds == null) {
            throw new IllegalArgumentException("数据源不存在: " + datasourceId);
        }
        return sysDatasourceService.getConnection(ds);
    }

    @Override
    public String getDbType(Long datasourceId) {
        if (sysDatasourceService == null) return null;

        SysDatasourceDO ds = sysDatasourceService.getById(datasourceId);
        if (ds == null) return null;

        return ds.getDbType() != null ? ds.getDbType() : "mysql";
    }

    @Override
    public void executeSqls(Long datasourceId, List<String> sqls) {
        if (sqls == null || sqls.isEmpty()) return;
        if (sysDatasourceService == null) {
            throw new IllegalStateException("SysDatasourceService 不可用");
        }

        try (Connection conn = getConnection(datasourceId);
             Statement stmt = conn.createStatement()) {
            conn.setAutoCommit(false);
            for (String sql : sqls) {
                if (sql == null || sql.isBlank()) continue;
                validateDdlSafety(sql);
                log.info("系统数据源执行SQL: {}", sql.substring(0, Math.min(sql.length(), 200)));
                stmt.execute(sql);
            }
            conn.commit();
            log.info("系统数据源成功执行 {} 条 SQL", sqls.size());
        } catch (Exception e) {
            log.error("系统数据源执行SQL失败: {}", e.getMessage(), e);
            throw new RuntimeException("系统数据源执行SQL失败: " + e.getMessage(), e);
        }
    }

    /**
     * DDL 安全校验 — 拒绝高危操作
     */
    private void validateDdlSafety(String sql) {
        String upper = sql.trim().toUpperCase();
        if (upper.startsWith("DROP DATABASE") || upper.startsWith("DROP TABLESPACE")
                || upper.startsWith("DROP USER") || upper.contains("INFORMATION_SCHEMA")
                || upper.contains("MYSQL.SYSTEM") || upper.contains("PG_CATALOG")) {
            throw new IllegalArgumentException("SQL 包含被禁止的高危操作: " + sql.substring(0, Math.min(100, sql.length())));
        }
        if (upper.contains(";") && !upper.trim().endsWith(";")) {
            throw new IllegalArgumentException("SQL 包含多条语句，已拒绝: " + sql.substring(0, Math.min(100, sql.length())));
        }
    }
}
