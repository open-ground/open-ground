package io.github.openground.common.log.enums;

/**
 * 操作状态枚举
 *
 * @author open-ground
 * @version 1.0
 */
public enum OptStatus {

    /** 失败 */
    F("F", "失败"),
    /** 成功 */
    S("S", "成功");

    private final String key;
    private final String value;

    OptStatus(String key, String value) {
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
