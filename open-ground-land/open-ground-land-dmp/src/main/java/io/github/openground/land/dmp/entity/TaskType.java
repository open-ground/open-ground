package io.github.openground.land.dmp.entity;

/**
 * 数据交换任务类型枚举
 *
 * @author open-ground
 * @since 1.0.7
 */
public enum TaskType {

    FILE_TO_DB("文件→库"),
    DB_TO_FILE("库→文件"),
    DB_TO_DB("库→库");

    private final String label;

    TaskType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean matches(String type) {
        return this.name().equals(type);
    }

    public static TaskType from(String type) {
        if (type == null) return null;
        for (TaskType t : values()) {
            if (t.name().equals(type)) return t;
        }
        return null;
    }
}
