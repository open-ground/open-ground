package io.github.openground.common.dbcheck;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * SQL 脚本路径解析器
 *
 * @author open-ground
 * @since 2026-06-18
 */
@Slf4j
public class ScriptPathResolver {

    private final DbCheckProperties properties;
    private final ResourceLoader resourceLoader;

    public ScriptPathResolver(DbCheckProperties properties, ResourceLoader resourceLoader) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
    }

    /**
     * 解析脚本目录的绝对路径
     */
    public String resolveScriptDir() {
        String scriptDir = properties.getScriptDir();
        Resource resource = resourceLoader.getResource("classpath:" + scriptDir);
        try {
            File dir = resource.getFile();
            if (dir.exists() && dir.isDirectory()) {
                return dir.getAbsolutePath();
            }
        } catch (IOException e) {
            log.warn("无法访问 classpath 脚本目录: classpath:{}", scriptDir, e);
        }
        // 兜底：尝试作为外部目录
        File externalDir = new File(scriptDir);
        if (externalDir.isAbsolute() && externalDir.exists()) {
            return externalDir.getAbsolutePath();
        }
        return scriptDir;
    }

    /**
     * 获取脚本目录下所有子目录（按数据库类型）
     */
    public List<String> listDbTypeDirs() {
        String baseDir = resolveScriptDir();
        File dir = new File(baseDir);
        if (!dir.exists() || !dir.isDirectory()) {
            return Collections.emptyList();
        }
        File[] subDirs = dir.listFiles(File::isDirectory);
        if (subDirs == null) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        for (File sub : subDirs) {
            result.add(sub.getName());
        }
        return result;
    }
}
