package io.github.openground.land.job;

import cn.hutool.core.util.StrUtil;
import io.github.openground.base.utils.CommonUtil;
import io.github.openground.land.api.domain.JobOut;
import io.github.openground.land.api.job.JobEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Shell 脚本命令任务
 * <p>
 * 参数：workPath（工作目录，必填）、command（要执行的命令，多个用逗号分隔，必填）、
 * sysEodDate（系统跑批日期，可选，用于替换 ${sysEodDate} 占位符）
 * </p>
 *
 * @author jack.zhang
 * @since 2026-06-26
 */
@Slf4j
@Service
public class CommandTask extends JobEngine {

    @Override
    public JobOut execute(Map<String, Object> map) throws Exception {
        JobOut out = new JobOut();
        Date start = new Date();
        log.info("任务执行start");
        try {
            String workPath = CommonUtil.getStringValueFromHashMap(map, "workPath");
            String command = CommonUtil.getStringValueFromHashMap(map, "command");
            String sysEodDate = CommonUtil.getStringValueFromHashMap(map, "sysEodDate");

            if (StrUtil.isEmpty(workPath)) {
                throw new IllegalArgumentException("workPath 参数不能为空");
            }
            if (StrUtil.isEmpty(command)) {
                throw new IllegalArgumentException("command 参数不能为空");
            }

            boolean windowsFlag = System.getProperty("os.name").toLowerCase().contains("win");
            if (windowsFlag) {
                throw new RuntimeException("暂不支持windows环境");
            }

            workPath = workPath.replace("${sysEodDate}", sysEodDate);
            command = command.replace("${sysEodDate}", sysEodDate);
            String output = executeShell(workPath, command);
            out.setSuccess(true);
            out.setMessage("任务执行成功:" + output);
            log.info("任务执行成功,耗时[{}]毫秒", new Date().getTime() - start.getTime());
        } catch (Exception e) {
            log.error("任务执行失败", e);
            out.setSuccess(false);
            out.setMessage("任务执行失败：" + e.getMessage());
        }
        return out;
    }

    /**
     * 执行 shell 命令
     *
     * @param workPath 工作目录路径
     * @param commands 要执行的命令（多个命令用逗号分隔）
     * @return 命令执行结果
     */
    private String executeShell(String workPath, String commands) throws Exception {
        if (StrUtil.isBlank(workPath) || StrUtil.isBlank(commands)) {
            throw new IllegalArgumentException("工作路径或命令不能为空");
        }

        StringBuilder output = new StringBuilder();
        String[] split = commands.split(",");

        if (!workPath.endsWith(File.separator)) {
            workPath = workPath + File.separator;
        }

        for (String command : split) {
            List<String> cmds = Arrays.asList("sh", "-c", command.trim());
            log.info("执行工作目录: {}, 命令: {}", workPath, command);

            Process process = new ProcessBuilder(cmds)
                    .directory(new File(workPath))
                    .start();

            int exitCode = process.waitFor();

            // 读取错误输出
            try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String errorLine;
                while ((errorLine = errorReader.readLine()) != null) {
                    output.append(errorLine).append("\n");
                }
            }

            if (exitCode != 0) {
                throw new Exception("shell command: [" + command + "] 执行失败: " + output + " - Exit Code: " + exitCode);
            }

            // 读取命令输出
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    if (output.length() > 2048) {
                        output.setLength(2048);
                        output.append("\n...(输出截断)");
                        break;
                    }
                }
            }
            log.info("命令输出: {}, 退出码: {}", output, exitCode);
        }
        return output.toString();
    }

}
