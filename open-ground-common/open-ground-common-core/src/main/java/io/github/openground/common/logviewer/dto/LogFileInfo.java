package io.github.openground.common.logviewer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 日志文件信息 DTO
 *
 * @author ground-auth
 * @since 2026-06-16
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogFileInfo {

    /** 文件名 */
    private String name;

    /** 文件大小（字节） */
    private long size;

    /** 文件大小（可读格式） */
    private String sizeDisplay;

    /** 最后修改时间 */
    private String lastModified;
}
