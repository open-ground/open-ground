package io.github.openground.common.logviewer;

import io.github.openground.common.logviewer.dto.LogFileInfo;
import io.github.openground.common.logviewer.dto.LogSearchRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 日志文件服务
 * <p>提供日志文件列表、内容读取、关键字搜索和实时 tail 功能。
 *
 * @author open-ground
 * @since 2026-06-18
 */
@Slf4j
@Service
public class LogService {

    private final LogProperties logProperties;

    /** 活跃的 tail 任务，key = sessionId，用于前端断开时清理 */
    private final Map<String, TailTask> tailTasks = new ConcurrentHashMap<>();

    public LogService(LogProperties logProperties) {
        this.logProperties = logProperties;
    }

    /**
     * 获取指定日期的日志文件列表
     *
     * @param date 日期字符串，格式 yyyy-MM-dd
     * @return 日志文件信息列表（按修改时间倒序）
     */
    public List<LogFileInfo> listFiles(String date) {
        String dateDir = resolveBaseDir() + File.separator + date;
        File dir = new File(dateDir);
        if (!dir.exists() || !dir.isDirectory()) {
            log.warn("日志目录不存在: {}", dateDir);
            return Collections.emptyList();
        }

        File[] files = dir.listFiles((d, name) -> name.endsWith(".log"));
        if (files == null || files.length == 0) {
            return Collections.emptyList();
        }

        List<LogFileInfo> result = new ArrayList<>();
        for (File file : files) {
            LogFileInfo info = new LogFileInfo();
            info.setName(file.getName());
            info.setSize(file.length());
            info.setSizeDisplay(formatSize(file.length()));
            info.setLastModified(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(file.lastModified())));
            result.add(info);
        }

        // 按修改时间倒序
        result.sort(Comparator.comparing(LogFileInfo::getLastModified).reversed());
        return result;
    }

    /**
     * 获取可用的日期列表（日志目录下的子目录）
     *
     * @return 日期字符串列表（倒序）
     */
    public List<String> listDates() {
        File baseDir = new File(resolveBaseDir());
        if (!baseDir.exists() || !baseDir.isDirectory()) {
            return Collections.emptyList();
        }

        File[] dirs = baseDir.listFiles(File::isDirectory);
        if (dirs == null || dirs.length == 0) {
            return Collections.emptyList();
        }

        List<String> dates = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        for (File dir : dirs) {
            // 验证目录名是否为日期格式
            try {
                sdf.parse(dir.getName());
                dates.add(dir.getName());
            } catch (Exception e) {
                // 跳过非日期目录
            }
        }
        dates.sort(Collections.reverseOrder());
        return dates;
    }

    /**
     * 读取日志文件内容（从末尾倒序分页读取）
     *
     * @param date     日期
     * @param fileName 文件名
     * @param offset   偏移量（已读取的行数）
     * @param limit    本次读取行数
     * @return 日志行列表（从旧到新排列）
     */
    public List<String> readContent(String date, String fileName, long offset, int limit) {
        File file = resolveFile(date, fileName);
        if (file == null) {
            return Collections.emptyList();
        }

        List<String> lines = new ArrayList<>();
        try {
            // 先统计总行数
            long totalLines = countLines(file);
            if (totalLines == 0) {
                return lines;
            }

            // 计算从哪一行开始读（从末尾倒推）
            long startLine = Math.max(0, totalLines - offset - limit);
            long endLine = totalLines - offset;

            if (startLine >= endLine) {
                return lines;
            }

            // 流式读取指定行范围
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                String line;
                long lineNum = 0;
                while ((line = reader.readLine()) != null) {
                    if (lineNum >= startLine && lineNum < endLine) {
                        lines.add(line);
                    }
                    if (lineNum >= endLine) {
                        break;
                    }
                    lineNum++;
                }
            }
        } catch (IOException e) {
            log.error("读取日志文件失败: {}", file.getAbsolutePath(), e);
        }

        return lines;
    }

    /**
     * 搜索日志文件内容
     *
     * @param date     日期
     * @param fileName 文件名
     * @param request  搜索请求（关键字、级别过滤）
     * @return 匹配的日志行列表
     */
    public List<String> search(String date, String fileName, LogSearchRequest request) {
        File file = resolveFile(date, fileName);
        if (file == null) {
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>();
        String keyword = request.getKeyword();
        String levelFilter = request.getLevel();
        int limit = Math.min(request.getLimit(), 1000);
        int offset = request.getOffset();
        int skipped = 0;
        int matched = 0;

        // 解析级别过滤列表
        List<String> levels = null;
        if (levelFilter != null && !levelFilter.isEmpty()) {
            levels = Arrays.asList(levelFilter.toUpperCase().split(","));
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null && result.size() < limit) {
                // 级别过滤
                if (levels != null && !levels.isEmpty()) {
                    boolean levelMatch = false;
                    for (String lv : levels) {
                        if (line.contains(lv.trim())) {
                            levelMatch = true;
                            break;
                        }
                    }
                    if (!levelMatch) {
                        continue;
                    }
                }

                // 关键字过滤
                if (keyword != null && !keyword.isEmpty()) {
                    if (!line.toLowerCase().contains(keyword.toLowerCase())) {
                        continue;
                    }
                }

                // 偏移量跳过
                if (skipped < offset) {
                    skipped++;
                    continue;
                }

                result.add(line);
                matched++;
            }
        } catch (IOException e) {
            log.error("搜索日志文件失败: {}", file.getAbsolutePath(), e);
        }

        return result;
    }

    /**
     * 建立 SSE 连接，实时 tail 日志文件
     *
     * @param date      日期
     * @param fileName  文件名
     * @param sessionId 会话标识（用于取消）
     * @return SseEmitter
     */
    public SseEmitter tail(String date, String fileName, String sessionId) {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L); // 30 分钟超时

        // 立即发送初始注释事件，触发响应头 flush，确保浏览器 EventSource 能收到 text/event-stream
        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException e) {
            log.warn("SSE 初始事件发送失败", e);
        }

        File file = resolveFile(date, fileName);
        if (file == null) {
            try {
                emitter.send(SseEmitter.event().name("error").data("日志文件不存在"));
                emitter.complete();
            } catch (IOException e) {
                // ignore
            }
            return emitter;
        }

        TailTask task = new TailTask(file, emitter, sessionId);
        tailTasks.put(sessionId, task);

        // 注册清理回调
        emitter.onCompletion(() -> {
            task.stop();
            tailTasks.remove(sessionId);
            log.debug("SSE 连接正常关闭: sessionId={}", sessionId);
        });
        emitter.onTimeout(() -> {
            task.stop();
            tailTasks.remove(sessionId);
            log.debug("SSE 连接超时: sessionId={}", sessionId);
        });
        emitter.onError(e -> {
            task.stop();
            tailTasks.remove(sessionId);
            log.debug("SSE 连接异常: sessionId={}, error={}", sessionId, e.getMessage());
        });

        // 启动 tail 线程
        Thread tailThread = new Thread(task, "log-tail-" + sessionId);
        tailThread.setDaemon(true);
        tailThread.start();

        return emitter;
    }

    /**
     * 停止指定会话的 tail 任务
     */
    public void stopTail(String sessionId) {
        TailTask task = tailTasks.remove(sessionId);
        if (task != null) {
            task.stop();
        }
    }

    // ========== 内部方法 ==========

    /**
     * 解析日志根目录的绝对路径
     */
    private String resolveBaseDir() {
        String baseDir = logProperties.getBaseDir();
        File dir = new File(baseDir);
        if (!dir.isAbsolute()) {
            // 相对路径：相对于当前工作目录
            dir = new File(System.getProperty("user.dir"), baseDir);
        }
        return dir.getAbsolutePath();
    }

    /**
     * 解析日志文件
     */
    private File resolveFile(String date, String fileName) {
        String baseDir = resolveBaseDir();
        File file = new File(baseDir + File.separator + date + File.separator + fileName);
        if (!file.exists() || !file.isFile()) {
            log.warn("日志文件不存在: {}", file.getAbsolutePath());
            return null;
        }
        return file;
    }

    /**
     * 统计文件行数
     */
    private long countLines(File file) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            long count = 0;
            while (reader.readLine() != null) {
                count++;
            }
            return count;
        }
    }

    /**
     * 格式化文件大小
     */
    private String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        }
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    // ========== 内部类 ==========

    /**
     * Tail 任务，在后台线程中持续监控文件变化
     */
    private static class TailTask implements Runnable {

        private final File file;
        private final SseEmitter emitter;
        private final String sessionId;
        private volatile boolean running = true;

        /** 轮询间隔（毫秒） */
        private static final long POLL_INTERVAL_MS = 500;

        TailTask(File file, SseEmitter emitter, String sessionId) {
            this.file = file;
            this.emitter = emitter;
            this.sessionId = sessionId;
        }

        @Override
        public void run() {
            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                // 定位到文件末尾，只读取新增内容
                long lastPosition = raf.length();
                raf.seek(lastPosition);
                long lastModified = file.lastModified();

                while (running) {
                    long currentLength = raf.length();
                    long currentModified = file.lastModified();

                    // 检测文件轮转（文件变小或 inode 变化）
                    if (currentLength < lastPosition || currentModified != lastModified) {
                        // 文件可能被轮转，尝试重新打开
                        if (currentLength < lastPosition) {
                            // 文件被截断/轮转，从头开始读
                            lastPosition = 0;
                        }
                        lastModified = currentModified;
                    }

                    // 读取新增内容
                    if (currentLength > lastPosition) {
                        raf.seek(lastPosition);
                        String line;
                        while ((line = raf.readLine()) != null) {
                            if (!running) break;
                            // 处理编码（RandomAccessFile.readLine() 使用 ISO-8859-1）
                            String decoded = new String(line.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
                            try {
                                emitter.send(SseEmitter.event()
                                        .name("log")
                                        .data(decoded));
                            } catch (IOException e) {
                                // 前端断开连接
                                running = false;
                                break;
                            }
                        }
                        lastPosition = raf.getFilePointer();
                    }

                    if (running) {
                        try {
                            Thread.sleep(POLL_INTERVAL_MS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            } catch (IOException e) {
                if (running) {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("error")
                                .data("读取日志文件异常: " + e.getMessage()));
                    } catch (IOException ex) {
                        // ignore
                    }
                }
            } finally {
                try {
                    emitter.complete();
                } catch (Exception e) {
                    // ignore
                }
            }
        }

        void stop() {
            running = false;
        }
    }
}
