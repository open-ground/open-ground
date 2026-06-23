package com.dcits.dbcheck;

/**
 * 脚本文件元信息 DTO
 *
 * <p>用于 GET /db-check/scripts 端点返回脚本列表，供前端选择。
 *
 * @author ground-auth
 * @since 2026-06-22
 */
public class ScriptInfo {

    /** 脚本唯一标识：modulePath/filename，例如 "db/auth/mysql/sys_user.sql" */
    private String scriptKey;

    /** 文件名，例如 "sys_user.sql" */
    private String fileName;

    /** 模块路径，例如 "db/auth/mysql" */
    private String modulePath;

    /** 文件大小（字节） */
    private long fileSize;

    public ScriptInfo() {}

    public ScriptInfo(String scriptKey, String fileName, String modulePath, long fileSize) {
        this.scriptKey = scriptKey;
        this.fileName = fileName;
        this.modulePath = modulePath;
        this.fileSize = fileSize;
    }

    public String getScriptKey() { return scriptKey; }
    public void setScriptKey(String scriptKey) { this.scriptKey = scriptKey; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getModulePath() { return modulePath; }
    public void setModulePath(String modulePath) { this.modulePath = modulePath; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
}
