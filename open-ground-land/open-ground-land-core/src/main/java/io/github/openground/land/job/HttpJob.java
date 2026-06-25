package io.github.openground.land.job;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import io.github.openground.base.utils.CommonUtil;
import io.github.openground.land.api.domain.JobOut;
import io.github.openground.land.api.job.JobEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * HTTP 任务 — 发送 HTTP POST 请求
 * <p>
 * 参数：url（请求地址，必填）、requestBody（请求体，必填）、timeout（超时时间，可选）
 * </p>
 *
 * @author jack.zhang
 * @since 2026-06-25
 */
@Slf4j
@Service
public class HttpJob extends JobEngine {

    @Override
    public JobOut execute(Map<String, Object> param) throws Exception {
        JobOut out = new JobOut();
        String url = CommonUtil.getStringValueFromHashMap(param, "url");
        String requestBody = CommonUtil.getStringValueFromHashMap(param, "requestBody");
        String timeoutStr = CommonUtil.getStringValueFromHashMap(param, "timeout");
        String sysEodDate = CommonUtil.getStringValueFromHashMap(param, "sysEodDate");

        if (StrUtil.isBlank(url) || StrUtil.isBlank(requestBody)) {
            out.setSuccess(false);
            out.setMessage("url 或 requestBody 不能为空");
            return out;
        }

        if (StrUtil.isNotBlank(sysEodDate)) {
            requestBody = requestBody.replace("${sysEodDate}", sysEodDate);
        }

        try {
            HttpRequest request = HttpRequest.post(url)
                    .body(requestBody)
                    .contentType("application/json;charset=UTF-8");

            if (StrUtil.isNotBlank(timeoutStr)) {
                request.timeout(Integer.parseInt(timeoutStr));
            }

            HttpResponse response = request.execute();
            String body = response.body();

            if (response.getStatus() != 200) {
                out.setSuccess(false);
                out.setMessage("HTTP " + response.getStatus() + ": " + body);
                return out;
            }

            // 检查响应中的业务状态
            if (JSONUtil.isTypeJSON(body)) {
                JSONObject json = JSONUtil.parseObj(body);
                if (json.containsKey("sysHead")) {
                    JSONObject sysHead = json.getJSONObject("sysHead");
                    String retStatus = sysHead.getStr("retStatus");
                    if ("F".equals(retStatus)) {
                        out.setSuccess(false);
                        out.setMessage(body);
                        return out;
                    }
                } else {
                    // 无 sysHead 时，检查是否包含成功码
                    if (!body.contains("0000") && !body.contains("000000")) {
                        // 不直接判失败，记录日志
                        log.info("HTTP响应未包含成功码: {}", body);
                    }
                }
            }

            out.setSuccess(true);
            out.setMessage(body);
        } catch (Exception e) {
            log.error("HTTP调用异常", e);
            out.setSuccess(false);
            out.setMessage(e.getMessage());
        }
        return out;
    }

}
