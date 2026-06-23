package io.github.openground.common.dbcheck;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * SQL 脚本扫描器
 * 扫描和读取 SQL 脚本文件
 *
 * @author open-ground
 * @since 2026-06-18
 */
@Slf4j
@Component
public class SqlScriptScanner {

    private final ScriptPathResolver scriptPathResolver;
    private final DatabaseTypeDetector databaseTypeDetector;
    private final DbCheckProperties dbCheckProperties;

    public SqlScriptScanner(ScriptPathResolver scriptPathResolver, DatabaseTypeDetector databaseTypeDetector,
                            DbCheckProperties dbCheckProperties) {
        this.scriptPathResolver = scriptPathResolver;
        this.databaseTypeDetector = databaseTypeDetector;
        this.dbCheckProperties = dbCheckProperties;
    }

    /**
     * 扫描 SQL 脚本并返回内容列表
     *
     * @param locations 路径配置
     * @param dbType 数据库类型
     * @return SQL 脚本内容列表
     */
    public List<SqlScript> scanScripts(List<String> locations, String dbType) {
        List<SqlScript> scripts = new ArrayList<>();
        long maxBytes = dbCheckProperties.getMaxScriptSizeMb() * 1024L * 1024L;

        try {
            List<Resource> resources = scriptPathResolver.scanSqlScripts(locations, dbType);
            
            for (Resource resource : resources) {
                try {
                    // 检查文件大小，超限跳过
                    long fileSize = resource.contentLength();
                    if (fileSize > maxBytes) {
                        log.warn("跳过超大脚本文件: {} ({} MB > {} MB 限制)",
                                resource.getFilename(),
                                String.format("%.1f", fileSize / (1024.0 * 1024.0)),
                                dbCheckProperties.getMaxScriptSizeMb());
                        continue;
                    }

                    String content = readResourceContent(resource);
                    String modulePath = scriptPathResolver.extractModulePath(resource.getURI().toString());
                    
                    SqlScript script = new SqlScript();
                    script.setFileName(resource.getFilename());
                    script.setContent(content);
                    script.setModulePath(modulePath);
                    script.setScriptKey(modulePath != null ? modulePath + "/" + resource.getFilename() : resource.getFilename());
                    script.setFileSize(fileSize);
                    script.setResource(resource);
                    
                    scripts.add(script);
                    log.info("Loaded SQL script: {} from {}", resource.getFilename(), modulePath);
                } catch (Exception e) {
                    log.error("Failed to read SQL script: {}", resource.getFilename(), e);
                }
            }
        } catch (Exception e) {
            log.error("Failed to scan SQL scripts", e);
        }
        
        log.info("Total SQL scripts loaded: {}", scripts.size());
        return scripts;
    }

    /**
     * 读取资源内容
     *
     * @param resource 资源
     * @return 文件内容
     */
    private String readResourceContent(Resource resource) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }

    /**
     * SQL 脚本实体类
     */
    public static class SqlScript {
        private String fileName;
        private String content;
        private String modulePath;
        private Resource resource;

        /** 脚本唯一标识：modulePath/filename，例如 "db/auth/mysql/sys_user.sql" */
        private String scriptKey;

        /** 文件大小（字节） */
        private long fileSize;

        // Getters and Setters
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        
        public String getModulePath() { return modulePath; }
        public void setModulePath(String modulePath) { this.modulePath = modulePath; }
        
        public Resource getResource() { return resource; }
        public void setResource(Resource resource) { this.resource = resource; }

        public String getScriptKey() { return scriptKey; }
        public void setScriptKey(String scriptKey) { this.scriptKey = scriptKey; }

        public long getFileSize() { return fileSize; }
        public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    }
}
