package io.github.openground.test.base;

import io.github.openground.base.constant.ErrorCode;
import io.github.openground.base.dto.CommonResult;
import io.github.openground.base.exception.CommonException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CommonResult / CommonException / ErrorCode 测试
 * <p>验证统一响应格式和异常处理</p>
 *
 * @author open-ground
 */
@DisplayName("CommonResult / CommonException 测试")
class CommonResultTest {

    @Nested
    @DisplayName("CommonResult 构建")
    class CommonResultBuilder {

        @Test
        @DisplayName("success() 应返回 code=0000")
        void shouldReturnSuccess() {
            CommonResult<String> result = CommonResult.success("hello");
            assertEquals(ErrorCode.SUCCESS, result.getCode());
            assertEquals("Success", result.getMessage());
            assertEquals("hello", result.getData());
        }

        @Test
        @DisplayName("success() 无参应返回 null data")
        void shouldReturnSuccessWithoutData() {
            CommonResult<Object> result = CommonResult.success();
            assertEquals(ErrorCode.SUCCESS, result.getCode());
            assertNull(result.getData());
        }

        @Test
        @DisplayName("error() 应返回指定错误码")
        void shouldReturnError() {
            CommonResult<String> result = CommonResult.error(ErrorCode.NO_AUTH, "未认证");
            assertEquals(ErrorCode.NO_AUTH, result.getCode());
            assertEquals("未认证", result.getMessage());
            assertNull(result.getData());
        }

        @Test
        @DisplayName("error() 带数据")
        void shouldReturnErrorWithData() {
            Map<String, String> details = new HashMap<>();
            details.put("field", "username");
            CommonResult<Map<String, String>> result = CommonResult.error(
                    ErrorCode.PARAMETER_ILLEGAL_ERROR, "参数不合法", details);
            assertEquals(ErrorCode.PARAMETER_ILLEGAL_ERROR, result.getCode());
            assertEquals("参数不合法", result.getMessage());
            assertEquals(details, result.getData());
        }

        @Test
        @DisplayName("链式调用应正常工作")
        void shouldSupportChaining() {
            CommonResult<String> result = new CommonResult<String>()
                    .setCode("0000")
                    .setMessage("OK")
                    .setData("test");
            assertEquals("0000", result.getCode());
            assertEquals("OK", result.getMessage());
            assertEquals("test", result.getData());
        }
    }

    @Nested
    @DisplayName("CommonException")
    class CommonExceptionTest {

        @Test
        @DisplayName("应正确设置 code 和 msg")
        void shouldSetCodeAndMsg() {
            CommonException ex = new CommonException("1000", "服务器错误");
            assertEquals("1000", ex.getCode());
            assertEquals("服务器错误", ex.getMsg());
            assertEquals("服务器错误", ex.getMessage());
        }

        @Test
        @DisplayName("getMessage 应返回 msg 或 code")
        void shouldReturnMessage() {
            CommonException ex1 = new CommonException("1000", "服务器错误");
            assertEquals("服务器错误", ex1.getMessage());

            CommonException ex2 = new CommonException("1000", null);
            assertEquals("1000", ex2.getMessage());
        }

        @Test
        @DisplayName("链式调用应正常工作")
        void shouldSupportChaining() {
            CommonException ex = new CommonException()
                    .setCode("0401")
                    .setMsg("未认证");
            assertEquals("0401", ex.getCode());
            assertEquals("未认证", ex.getMsg());
        }

        @Test
        @DisplayName("应继承 RuntimeException")
        void shouldBeRuntimeException() {
            CommonException ex = new CommonException("1000", "error");
            assertInstanceOf(RuntimeException.class, ex);
        }
    }

    @Nested
    @DisplayName("ErrorCode 常量")
    class ErrorCodeConstants {

        @Test
        @DisplayName("成功码应为 0000")
        void successCode() {
            assertEquals("0000", ErrorCode.SUCCESS);
        }

        @Test
        @DisplayName("认证错误码应在 0400-0499 范围")
        void authErrorCodes() {
            assertEquals("0401", ErrorCode.NO_AUTH);
            assertEquals("0400", ErrorCode.BAD_CREDENTIALS);
            assertEquals("0403", ErrorCode.NO_LOGIN);
            assertEquals("0404", ErrorCode.NO_PERMISSIONS);
            assertEquals("0405", ErrorCode.FORBIDDEN);
            assertEquals("0406", ErrorCode.SESSION_EXPIRED);
        }

        @Test
        @DisplayName("系统错误码应在 1000-1999 范围")
        void systemErrorCodes() {
            assertEquals("1000", ErrorCode.SERVER_INTERNAL_ERROR);
            assertEquals("1001", ErrorCode.PARAMETER_MISSING_ERROR);
            assertEquals("1002", ErrorCode.PARAMETER_ILLEGAL_ERROR);
            assertEquals("1003", ErrorCode.RESOURCE_NOT_FOUND_ERROR);
            assertEquals("1006", ErrorCode.DATABASE_OPERATION_ERROR);
        }
    }
}
