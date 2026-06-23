package io.github.openground.common.dbcheck.extractor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 脚本路径解析器
 * 解析和扫描 SQL 脚本路径
 *
 * @author open-ground
 * @since 2026-06-18
 */
@Slf4j
@Component
public class ScriptPathResolver {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");

    /**
     * 解析脚本路径，替换占位符
     *
     * @param path 原始路径
     * @param dbType 数据库类型
     * @return 解析后的路径
     */
    public String resolvePath(String path, String dbType) {
        if (path == null || dbType == null) {
            return path;
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(path);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String placeholder = matcher.group(1);
            String replacement = "databaseType".equals(placeholder) ? dbType : "${" + placeholder + "}";
            matcher.appendReplacement(result, replacement);
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * 扫描指定路径下的 SQL 脚本
     *
     * @param locations 路径配置
     * @param dbType 数据库类型
     * @return SQL 脚本资源列表
     */
    public List<Resource> scanSqlScripts(List<String> locations, String dbType) {
        List<Resource> resources = new ArrayList<>();
        Set<String> uniquePaths = new LinkedHashSet<>();

        try {
            ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

            for (String location : locations) {
                String resolvedPath = resolvePath(location, dbType);
                log.info("Scanning SQL scripts from: {}", resolvedPath);

                try {
                    Resource[] locationResources = resolver.getResources(resolvedPath);
                    for (Resource resource : locationResources) {
                        String uri = resource.getURI().toString();
                        if (!uniquePaths.contains(uri)) {
                            uniquePaths.add(uri);
                            resources.add(resource);
                            log.info("Found SQL script: {}", resource.getFilename());
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to scan location: {} - {}", resolvedPath, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to scan SQL scripts", e);
        }

        log.info("Total SQL scripts found: {}", resources.size());
        return resources;
    }

    /**
     * 提取脚本的模块路径
     *
     * @param uri 资源 URI
     * @return 模块路径 (如: db/ai/mysql)
     */
    public String extractModulePath(String uri) {
        int dbIdx = uri.indexOf("/db/");
        if (dbIdx < 0) {
            return null;
        }
        String relativePath = uri.substring(dbIdx + 1);
        String[] parts = relativePath.split("/");
        if (parts.length >= 3) {
            return parts[0] + "/" + parts[1] + "/" + parts[2];
        }
        return null;
    }
}
