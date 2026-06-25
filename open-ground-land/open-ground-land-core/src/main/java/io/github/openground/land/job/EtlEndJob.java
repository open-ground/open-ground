package io.github.openground.land.job;

import cn.hutool.core.util.StrUtil;
import io.github.openground.base.utils.CommonUtil;
import io.github.openground.land.api.domain.JobOut;
import io.github.openground.land.api.job.JobEngine;
import io.github.openground.land.common.util.FileUtil;
import org.springframework.stereotype.Service;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

/**
 * ETL 结束任务
 * <p>
 * 任务执行结束后，根据参数配置生成 OK 文件。
 * 参数：sysEodDate（系统跑批日期）、endFilePath（文件路径）、endFileName（文件名）
 * </p>
 *
 * @author jack.zhang
 * @since 2026-06-26
 */
@Service
public class EtlEndJob extends JobEngine {

    private static final String DEFAULT_END_FILE_PATH = "logs";
    private static final String DEFAULT_END_FILE_NAME = ".ok";

    @Override
    public JobOut execute(Map<String, Object> param) throws Exception {
        JobOut out = new JobOut();
        try {
            String sysEodDate = CommonUtil.getStringValueFromHashMap(param, "sysEodDate");
            String endFilePath = CommonUtil.getStringValueFromHashMap(param, "endFilePath");
            String endFileName = CommonUtil.getStringValueFromHashMap(param, "endFileName");

            if (StrUtil.isEmpty(endFilePath)) {
                endFilePath = File.separator + DEFAULT_END_FILE_PATH + File.separator;
            }
            if (StrUtil.isEmpty(sysEodDate)) {
                sysEodDate = new SimpleDateFormat("yyyyMMdd").format(new Date());
            }
            if (StrUtil.isEmpty(endFileName)) {
                endFileName = sysEodDate + DEFAULT_END_FILE_NAME;
            }

            String fileFullName = endFilePath + sysEodDate + File.separator + endFileName;
            String content = sysEodDate + "ETL跑批完成";
            FileUtil.writeFile(fileFullName, content);

            out.setSuccess(true);
            out.setMessage("任务执行成功");
        } catch (Exception e) {
            out.setSuccess(false);
            out.setMessage("任务执行异常" + e.getMessage());
        }
        return out;
    }

}
