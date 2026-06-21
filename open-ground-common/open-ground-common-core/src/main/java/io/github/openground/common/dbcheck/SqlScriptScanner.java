package io.github.openground.common.dbcheck;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * SQL 脚本文件扫描器
 *
 * @author open-ground
 * @since 2026-06-18
 */
@Slf4j
public class SqlScriptScanner {

    private final ScriptPathResolver scriptPathResolver;
    private final DbCheckProperties properties;

    public SqlScriptScanner(ScriptPathResolver scriptPathResolver, DbCheckProperties properties) {
        this.scriptPathResolver = scriptPathResolver;
        this.properties = properties;
    }

    /**
     * 扫描指定数据库类型的 SQL 脚本文件
     *
     * @param dbType 数据库类型
     * @return 脚本文件列表
     */
    public List<File> scanSqlFiles(String dbType) {
        String baseDir = scriptPathResolver.resolveScriptDir();
        File dbDir = new File(baseDir, dbType);
        if (!dbDir.exists() || !dbDir.isDirectory()) {
            log.warn("脚本目录不存在: {}", dbDir.getAbsolutePath());
            return new ArrayList<>();
        }
        List<File> sqlFiles = new ArrayList<>();
        collectSqlFiles(dbDir, sqlFiles);
        return sqlFiles;
    }

    /**
     * 递归收集 .sql 文件
     */
    private void collectSqlFiles(File dir, List<File> result) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                collectSqlFiles(file, result);
            } else if (file.getName().endsWith(".sql")) {
                result.add(file);
            }
        }
    }

    /**
     * 读取 SQL 文件内容
     */
    public String readSqlFile(File file) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (IOException e) {
            log.error("读取 SQL 文件失败: {}", file.getAbsolutePath(), e);
        }
        return sb.toString();
    }
}
