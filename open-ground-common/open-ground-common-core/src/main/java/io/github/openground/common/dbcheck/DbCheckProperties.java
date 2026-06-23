package io.github.openground.common.dbcheck;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * DbCheck 配置属性
 * 支持多数据库类型检查和数据同步配置
 *
 * @author open-ground
 * @since 2026-06-18
 */
@Component
@ConfigurationProperties("ground.db-check")
@Data
public class DbCheckProperties {

    /**
     * 是否启用 DbCheck 功能
     */
    private boolean enabled = false;

    /**
     * 检查模式
     * check-only: 仅检查，不执行覆盖
     * check-and-cover: 检查并覆盖不一致的表结构
     */
    private String mode = "check-and-cover";

    /**
     * 是否启用数据检查
     */
    private boolean dataCheck = true;

    /**
     * 是否启用注释检查
     */
    private boolean commentCheck = true;

    /**
     * 是否删除表中多余数据（当 dataCheck 为 true 时生效）
     */
    private boolean deleteExtraData = false;

    /**
     * 脚本路径配置，类似 Flyway 的 locations
     * 支持多个路径，按顺序执行
     */
    private List<String> locations = new ArrayList<>();

    /**
     * 数据库类型，自动检测时可不配置
     * 支持：mysql, oracle, dm, postgresql
     */
    private String databaseType;

    /**
     * 是否在应用启动时自动执行检查
     */
    private boolean autoCheckOnStartup = false;

    /**
     * 检查失败时是否阻止应用启动
     */
    private boolean failOnMismatch = false;

    /**
     * 初始化脚本执行顺序
     */
    private List<String> initScriptOrder = new ArrayList<>();

    /**
     * 脚本文件大小上限（MB），超过此大小的文件将被跳过
     */
    private long maxScriptSizeMb = 5;

    /**
     * 构造函数，初始化默认路径
     */
    public DbCheckProperties() {
        // 默认扫描 db 目录下的所有数据库类型脚本
        locations.add("classpath*:db/*/${databaseType}/*.sql");
        locations.add("classpath*:db/*/${databaseType}/*.ddl");
    }
}
