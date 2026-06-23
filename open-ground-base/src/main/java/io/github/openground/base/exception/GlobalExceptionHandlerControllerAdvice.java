package io.github.openground.base.exception;

import io.github.openground.base.constant.ErrorCode;
import io.github.openground.base.dto.CommonResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.util.stream.Collectors;

/**
 * 全局异常处理
 *
 */
@Slf4j
@ControllerAdvice
@SuppressWarnings("all")
public class GlobalExceptionHandlerControllerAdvice {

    /**
     * 处理业务异常 CommonException
     *
     * @param request HTTP请求对象
     * @param e       业务异常
     * @return 统一错误响应
     */
    @ExceptionHandler(CommonException.class)
    public ResponseEntity<CommonResult> handleCommonException(HttpServletRequest request, CommonException e) {
        log.error("[URI: {}] 业务异常: {}", request.getRequestURI(), e.getMessage(), e);

        CommonResult result = new CommonResult();
        result.setCode(e.getCode());
        result.setMessage(e.getMessage());
        result.setData(null);

        return ResponseEntity.ok(result);
    }

    /**
     * 处理 @Valid 注解引发的参数校验异常
     *
     * @param e 参数校验异常
     * @return 统一错误响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CommonResult> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));

        log.warn("参数校验失败: {}", message);

        CommonResult result = new CommonResult();
        result.setCode(ErrorCode.PARAMETER_ILLEGAL_ERROR);
        result.setMessage("参数校验失败: " + message);
        result.setData(null);

        return ResponseEntity.ok(result);
    }

    /**
     * 处理 BindException 异常
     *
     * @param e 参数绑定异常
     * @return 统一错误响应
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<CommonResult> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));

        log.warn("参数绑定失败: {}", message);

        CommonResult result = new CommonResult();
        result.setCode(ErrorCode.PARAMETER_ILLEGAL_ERROR);
        result.setMessage("参数校验失败: " + message);
        result.setData(null);

        return ResponseEntity.ok(result);
    }

    /**
     * 处理 IllegalArgumentException 异常
     *
     * @param e 非法参数异常
     * @return 统一错误响应
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<CommonResult> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("非法参数: {}", e.getMessage());

        CommonResult result = new CommonResult();
        result.setCode(ErrorCode.PARAMETER_ILLEGAL_ERROR);
        result.setMessage("参数错误: " + e.getMessage());
        result.setData(null);

        return ResponseEntity.ok(result);
    }

    /**
     * 处理通用异常(兜底)
     *
     * @param request HTTP请求对象
     * @param e       系统异常
     * @return 统一错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonResult> handleException(HttpServletRequest request, HttpServletResponse response, Exception e) {
        // SSE 客户端断开导致的 AsyncRequestNotUsableException 是预期行为，降级为 DEBUG 日志
        if (e instanceof AsyncRequestNotUsableException
                && response.getContentType() != null
                && response.getContentType().contains(MediaType.TEXT_EVENT_STREAM_VALUE)) {
            log.debug("[URI: {}] SSE 客户端断开: {}", request.getRequestURI(), e.getMessage());
            return null;
        }

        log.error("[URI: {}] 系统异常: {}", request.getRequestURI(), e.getMessage(), e);

        // SSE 端点异常不返回 CommonResult（Content-Type 不匹配），直接返回 null 让容器处理
        if (response.getContentType() != null && response.getContentType().contains(MediaType.TEXT_EVENT_STREAM_VALUE)) {
            return null;
        }

        CommonResult result = new CommonResult();
        result.setCode(ErrorCode.SERVER_INTERNAL_ERROR);
        result.setMessage("系统内部错误: " + e.getMessage());
        result.setData(null);

        return ResponseEntity.ok(result);
    }
}
