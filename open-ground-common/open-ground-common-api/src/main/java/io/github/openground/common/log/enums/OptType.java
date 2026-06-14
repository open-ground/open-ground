package io.github.openground.common.log.enums;

/**
 * 操作类型枚举
 *
 * @author open-ground
 * @version 1.0
 */
public enum OptType {

    /** 查询 */
    QUERY("QUERY", "查询"),
    /** 远程查询 */
    QUERY_REMOTE("QUERY_REMOTE", "远程查询"),
    /** 查询树 */
    QUERY_TREE("QUERY_TREE", "查询树"),
    /** 根据主键查询 */
    QUERY_PK("QUERY_PK", "根据主键查询"),
    /** 插入 */
    INSERT("INSERT", "插入"),
    /** 修改 */
    UPDATE("UPDATE", "修改"),
    /** 批量修改 */
    UPDATE_ALL("UPDATE_ALL", "批量修改"),
    /** 全字段修改 */
    UPDATE_FULL("UPDATE_FULL", "全字段修改"),
    /** 删除 */
    DELETE("DELETE", "删除"),
    /** 批量删除 */
    DELETE_ALL("DELETE_ALL", "批量删除"),
    /** 授权 */
    GRANT("GRANT", "授权"),
    /** 导出 */
    EXPORT("EXPORT", "导出"),
    /** 导入 */
    IMPORT("IMPORT", "导入"),
    /** 清空 */
    CLEAN("CLEAN", "清空"),
    /** 核对 */
    CHECK("CHECK", "核对"),
    /** 批量核对 */
    CHECK_ALL("CHECK_ALL", "批量核对"),
    /** 启用 */
    START("START", "启用"),
    /** 停用 */
    STOP("STOP", "停用"),
    /** 审批 */
    APPROVE("APPROVE", "审批"),
    /** 分配 */
    DISTRIBUTE("DISTRIBUTE", "分配"),
    /** 认领 */
    CLAIM("CLAIM", "认领"),
    /** 其他 */
    OTHER("OTHER", "其他");

    private final String key;
    private final String value;

    OptType(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return this.key;
    }

    public String getValue() {
        return this.value;
    }
}
