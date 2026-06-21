package io.github.openground.common.logviewer.dto;

import lombok.Data;

/**
 * 日志搜索请求 DTO
 *
 * @author ground-auth
 * @since 2026-06-16
 */
@Data
public class LogSearchRequest {

    /** 搜索关键字 */
    private String keyword;

    /** 日志级别过滤（ERROR/WARN/INFO/DEBUG/TRACE），多个用逗号分隔 */
    private String level;

    /** 返回条数上限，默认 200 */
    private int limit = 200;

    /** 搜索偏移量（用于分页） */
    private int offset = 0;
}
