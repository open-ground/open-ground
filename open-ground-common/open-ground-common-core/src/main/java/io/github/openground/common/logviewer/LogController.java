package io.github.openground.common.logviewer;

import io.github.openground.base.constant.ErrorCode;
import io.github.openground.base.dto.CommonResult;
import io.github.openground.common.logviewer.dto.LogFileInfo;
import io.github.openground.common.logviewer.dto.LogNodeInfo;
import io.github.openground.common.logviewer.dto.LogSearchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

/**
 * 日志查看控制器
 *
 * <p>提供服务端日志文件的查看、搜索和实时 tail 功能。
 * 支持多节点部署场景，通过代理转发非本节点的请求。
 *
 * @author open-ground
 * @since 2026-06-18
 */
@Slf4j
@Tag(name = "日志查看")
@RestController
@RequestMapping("/log")
@SuppressWarnings("all")
public class LogController {

    @Autowired
    private LogService logService;

    @Autowired
    private LogNodeProxyService proxyService;

    /**
     * 获取所有节点列表
     *
     * @return 节点信息列表
     */
    @Operation(summary = "获取所有节点列表")
    @GetMapping("/nodes")
    public ResponseEntity<?> listNodes() {
        List<LogNodeInfo> nodes = proxyService.listNodes();
        return ResponseEntity.ok(
                new CommonResult()
                        .setCode(ErrorCode.SUCCESS)
                        .setMessage(ErrorCode.SUCCESS_MSG)
                        .setData(nodes)
        );
    }

    /**
     * 获取可用的日期列表（日志目录下的子目录）
     *
     * @param node 节点名称，为空或当前节点则查本地
     * @return 日期字符串列表
     */
    @Operation(summary = "获取可用的日期列表")
    @GetMapping("/dates")
    public ResponseEntity<?> listDates(
            @RequestParam(value = "node", required = false) String node) {
        List<String> dates;
        if (node != null && !proxyService.isCurrentNode(node)) {
            dates = proxyService.proxyListDates(node);
        } else {
            dates = logService.listDates();
        }
        return ResponseEntity.ok(
                new CommonResult()
                        .setCode(ErrorCode.SUCCESS)
                        .setMessage(ErrorCode.SUCCESS_MSG)
                        .setData(dates)
        );
    }

    /**
     * 获取指定日期的日志文件列表
     *
     * @param node 节点名称
     * @param date 日期（yyyy-MM-dd）
     * @return 日志文件信息列表
     */
    @Operation(summary = "获取日志文件列表")
    @GetMapping("/files")
    public ResponseEntity<?> listFiles(
            @RequestParam(value = "node", required = false) String node,
            @RequestParam("date") String date) {
        List<LogFileInfo> files;
        if (node != null && !proxyService.isCurrentNode(node)) {
            files = proxyService.proxyListFiles(node, date);
        } else {
            files = logService.listFiles(date);
        }
        return ResponseEntity.ok(
                new CommonResult()
                        .setCode(ErrorCode.SUCCESS)
                        .setMessage(ErrorCode.SUCCESS_MSG)
                        .setData(files)
        );
    }

    /**
     * 读取日志文件内容（倒序分页）
     *
     * @param node     节点名称
     * @param date     日期
     * @param fileName 文件名
     * @param offset   偏移量（已读取行数）
     * @param limit    本次读取行数，默认 200
     * @return 日志行列表
     */
    @Operation(summary = "读取日志文件内容")
    @GetMapping("/content")
    public ResponseEntity<?> readContent(
            @RequestParam(value = "node", required = false) String node,
            @RequestParam("date") String date,
            @RequestParam("fileName") String fileName,
            @RequestParam(value = "offset", defaultValue = "0") long offset,
            @RequestParam(value = "limit", defaultValue = "200") int limit) {
        List<String> lines;
        if (node != null && !proxyService.isCurrentNode(node)) {
            lines = proxyService.proxyReadContent(node, date, fileName, offset, limit);
        } else {
            lines = logService.readContent(date, fileName, offset, limit);
        }
        return ResponseEntity.ok(
                new CommonResult()
                        .setCode(ErrorCode.SUCCESS)
                        .setMessage(ErrorCode.SUCCESS_MSG)
                        .setData(lines)
        );
    }

    /**
     * 搜索日志文件内容
     *
     * @param node     节点名称
     * @param date     日期
     * @param fileName 文件名
     * @param keyword  搜索关键字
     * @param level    日志级别过滤（多个用逗号分隔）
     * @param limit    返回条数上限
     * @param offset   偏移量
     * @return 匹配的日志行列表
     */
    @Operation(summary = "搜索日志文件内容")
    @GetMapping("/search")
    public ResponseEntity<?> search(
            @RequestParam(value = "node", required = false) String node,
            @RequestParam("date") String date,
            @RequestParam("fileName") String fileName,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "level", required = false) String level,
            @RequestParam(value = "limit", defaultValue = "200") int limit,
            @RequestParam(value = "offset", defaultValue = "0") int offset) {
        LogSearchRequest request = new LogSearchRequest();
        request.setKeyword(keyword);
        request.setLevel(level);
        request.setLimit(limit);
        request.setOffset(offset);

        List<String> lines;
        if (node != null && !proxyService.isCurrentNode(node)) {
            lines = proxyService.proxySearch(node, date, fileName, request);
        } else {
            lines = logService.search(date, fileName, request);
        }
        return ResponseEntity.ok(
                new CommonResult()
                        .setCode(ErrorCode.SUCCESS)
                        .setMessage(ErrorCode.SUCCESS_MSG)
                        .setData(lines)
        );
    }

    /**
     * 实时 tail 日志文件（SSE 长连接）
     *
     * <p>建立 SSE 连接后，服务端持续推送新增的日志行。
     * 事件格式：
     * <ul>
     *   <li>event: log / data: 日志行内容</li>
     *   <li>event: error / data: 错误信息</li>
     * </ul>
     *
     * @param node      节点名称
     * @param date      日期
     * @param fileName  文件名
     * @param sessionId 会话标识（可选，用于取消）
     * @return SseEmitter
     */
    @Operation(summary = "实时 tail 日志文件（SSE）")
    @GetMapping(value = "/tail", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter tail(
            @RequestParam(value = "node", required = false) String node,
            @RequestParam("date") String date,
            @RequestParam("fileName") String fileName,
            @RequestParam(value = "sessionId", required = false) String sessionId,
            HttpServletResponse response) {
        String sid = (sessionId != null && !sessionId.isEmpty()) ? sessionId : UUID.randomUUID().toString();

        // SSE 响应头配置：确保所有中间代理不缓冲响应流
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Connection", "keep-alive");
        // 强制覆盖 Filter 中设置的 Content-Type，确保浏览器 EventSource 能识别 SSE 响应
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);

        if (node != null && !proxyService.isCurrentNode(node)) {
            log.info("代理 SSE tail: node={}, date={}, file={}, sessionId={}", node, date, fileName, sid);
            return proxyService.proxyTail(node, date, fileName, sid);
        }

        log.info("本地 SSE tail: date={}, file={}, sessionId={}", date, fileName, sid);
        return logService.tail(date, fileName, sid);
    }

    /**
     * 停止 tail 会话
     *
     * @param sessionId 会话标识
     */
    @Operation(summary = "停止 tail 会话")
    @GetMapping("/tail/stop")
    public ResponseEntity<?> stopTail(@RequestParam("sessionId") String sessionId) {
        logService.stopTail(sessionId);
        return ResponseEntity.ok(
                new CommonResult()
                        .setCode(ErrorCode.SUCCESS)
                        .setMessage("已停止")
        );
    }
}
