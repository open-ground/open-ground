package io.github.openground.common.log.aspect;

import com.alibaba.fastjson.JSONObject;
import io.github.openground.base.utils.IpUtils;
import io.github.openground.base.utils.RequestUtil;
import io.github.openground.common.keygen.KeyGenerator;
import io.github.openground.common.log.annotation.OptLog;
import io.github.openground.common.log.domain.SysOptLog;
import io.github.openground.common.log.enums.OptStatus;
import io.github.openground.common.log.event.OptLogEvent;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.RequestMethod;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

/**
 * 操作日志切面
 * <p>拦截 {@link OptLog} 注解，收集日志数据后通过 Spring 事件机制发布，
 * 由 {@code OptLogEventListener} 异步消费处理。</p>
 * <p>使用场景：</p>
 * <ul>
 *   <li><b>集成部署</b>：auth-core 自身的 AuthLogAspect 已处理，此切面可不启用</li>
 *   <li><b>分离部署</b>：业务模块（flow/dmp/ai）引入 ground-auth-springcloud 等实现，
 *       自动注入 LogSender，日志通过 Feign 等远程写入 Auth 数据库</li>
 * </ul>
 * <p>收集阶段同步执行（需要 Servlet 请求上下文），发送阶段由 {@code @Async} 监听器异步处理。</p>
 *
 * @author open-ground
 * @version 1.0
 */
@Slf4j
@Aspect
public class OptLogAspect {

    /** Spring 事件发布器 */
    @Autowired
    private ApplicationEventPublisher publisher;

    private int logLength = 2000;

    public void setLogLength(int logLength) {
        this.logLength = logLength;
    }

    @Pointcut("@annotation(io.github.openground.common.log.annotation.OptLog)")
    public void logPointCut() {
    }

    @AfterReturning(pointcut = "logPointCut()")
    public void doAfter(JoinPoint joinPoint) {
        handleLog(joinPoint, null);
    }

    @AfterThrowing(value = "logPointCut()", throwing = "e")
    public void doAfterThrowing(JoinPoint joinPoint, Exception e) {
        handleLog(joinPoint, e);
    }

    protected void handleLog(final JoinPoint joinPoint, final Exception e) {
        try {
            OptLog controllerLog = getAnnotationLog(joinPoint);
            if (controllerLog == null) {
                return;
            }

            SysOptLog optLog = new SysOptLog();
            optLog.setLogId(KeyGenerator.getInternalKey() + "");

            HttpServletRequest request = RequestUtil.getRequest();
            if (request != null) {
                optLog.setIpAddress(IpUtils.getIpAddr(request));
                optLog.setOptUrl(request.getRequestURI());
                optLog.setUserId(request.getHeader("userCode"));
            }

            optLog.setSysTime(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));

            if (e != null) {
                optLog.setOptStatus(OptStatus.F.getKey());
                optLog.setErrMsg(truncate(e.getMessage(), 1000));
            } else {
                optLog.setOptStatus(OptStatus.S.getKey());
            }

            String className = joinPoint.getTarget().getClass().getName();
            String methodName = joinPoint.getSignature().getName();
            optLog.setOptMethod(className + "." + methodName + "()");

            optLog.setOptType(controllerLog.optType().getKey());
            optLog.setOptRemark(controllerLog.optRemark());

            if (controllerLog.isSaveRequestData() && request != null) {
                String params = getRequestParams(request);
                optLog.setOptParam(truncate(params, logLength));
            }

            // 发布事件，由 OptLogEventListener 异步消费
            publisher.publishEvent(new OptLogEvent(optLog));
            log.info("OptLogEvent publish -> {}", optLog.toString());

        } catch (Exception exp) {
            log.warn("操作日志记录异常", exp);
        }
    }

    private OptLog getAnnotationLog(JoinPoint joinPoint) throws Exception {
        Signature signature = joinPoint.getSignature();
        MethodSignature methodSignature = (MethodSignature) signature;
        Method method = methodSignature.getMethod();
        if (method != null) {
            return method.getAnnotation(OptLog.class);
        }
        return null;
    }

    private String getRequestParams(HttpServletRequest request) {
        String method = request.getMethod();
        try {
            if (RequestMethod.GET.name().equals(method)) {
                Map<String, String[]> map = request.getParameterMap();
                return JSONObject.toJSONString(map);
            } else {
                InputStream in = request.getInputStream();
                if (in != null) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    return sb.toString();
                }
            }
        } catch (Exception ex) {
            log.warn("获取请求参数失败", ex);
        }
        return "";
    }

    private String truncate(String str, int maxBytes) {
        if (str == null) return null;
        try {
            byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
            if (bytes.length <= maxBytes) return str;
            return new String(bytes, 0, maxBytes, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return str.length() <= maxBytes ? str : str.substring(0, maxBytes);
        }
    }
}
