package io.github.openground.land.job;

import cn.hutool.core.util.StrUtil;
import io.github.openground.base.utils.CommonUtil;
import io.github.openground.land.api.domain.JobOut;
import io.github.openground.land.api.job.JobEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;

/**
 * Shell 任务 — 执行 Shell 命令
 * <p>
 * 参数：shellPath（脚本路径，必填）、shellParam（脚本参数，可选）、bash（Shell 类型，默认 sh）
 * </p>
 *
 * @author jack.zhang
 * @since 2026-06-25
 */
@Slf4j
@Service
public class ShellTask extends JobEngine {

    @Override
    public JobOut execute(Map<String, Object> param) throws Exception {
        JobOut out = new JobOut();
        String shellPath = CommonUtil.getStringValueFromHashMap(param, "shellPath");
        String shellParam = CommonUtil.getStringValueFromHashMap(param, "shellParam");
        String sysEodDate = CommonUtil.getStringValueFromHashMap(param, "sysEodDate");

        if (StrUtil.isBlank(shellPath)) {
            throw new IllegalArgumentException("shellPath 不能为空");
        }

        if (StrUtil.isNotBlank(shellParam)) {
            shellParam = shellParam.replace("${sysEodDate}", sysEodDate);
        }

        String bash = CommonUtil.getStringValueFromHashMap(param, "bash");
        if (StrUtil.isBlank(bash)) {
            bash = "sh ";
        }

        String command = bash + shellPath + " " + (shellParam != null ? shellParam : "");
        log.info("执行Shell命令: {}", command);

        try {
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            while ((line = errorReader.readLine()) != null) {
                output.append(line).append("\n");
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new Exception("Shell执行失败, exitCode=" + exitCode + ", output=" + output);
            }

            out.setSuccess(true);
            out.setMessage(output.toString());
        } catch (Exception e) {
            log.error("Shell执行异常", e);
            out.setSuccess(false);
            out.setMessage(e.getMessage());
        }
        return out;
    }

}
