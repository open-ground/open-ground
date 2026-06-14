package io.github.openground.base.constant;

/**
 * 统一错误码接口。
 *
 * <p>预定义了通用系统错误码。业务模块可通过实现此接口来扩展自定义错误码：</p>
 * <pre>{@code
 * public interface MyErrorCode extends ErrorCode {
 *     String ORDER_NOT_FOUND = "2001";
 *     String PAYMENT_FAILED = "2002";
 * }
 * }</pre>
 *
 * <h3>错误码区间约定</h3>
 * <ul>
 *   <li>{@code 0000} — 成功</li>
 *   <li>{@code 0400-0499} — 认证 / 授权错误</li>
 *   <li>{@code 1000-1999} — 通用系统错误</li>
 *   <li>{@code 2000-8999} — 业务模块自定义（预留）</li>
 *   <li>{@code 9000-9999} — 严重系统错误</li>
 * </ul>
 *
 * @author open-ground
 */
public interface ErrorCode {

    /** 操作成功 */
    String SUCCESS = "0000";
    String SUCCESS_MSG = "Success";

    // ==================== 认证 / 授权 (0400–0499) ====================
    /** 未认证 */
    String NO_AUTH = "0401";
    /** 凭证无效 */
    String BAD_CREDENTIALS = "0400";
    /** 未登录 */
    String NO_LOGIN = "0403";
    /** 无权限 */
    String NO_PERMISSIONS = "0404";
    /** 禁止访问 */
    String FORBIDDEN = "0405";
    /** 会话过期 */
    String SESSION_EXPIRED = "0406";

    // ==================== 通用系统错误 (1000–1999) ====================
    /** 服务器内部错误 */
    String SERVER_INTERNAL_ERROR = "1000";
    /** 参数缺失 */
    String PARAMETER_MISSING_ERROR = "1001";
    /** 参数不合法 */
    String PARAMETER_ILLEGAL_ERROR = "1002";
    /** 资源不存在 */
    String RESOURCE_NOT_FOUND_ERROR = "1003";
    /** 数据库操作失败 */
    String DATABASE_OPERATION_ERROR = "1006";

    // ==================== 严重系统错误 (9000–9999) ====================
    /** 数据库异常 */
    String DATABASE_EXCEPTION = "9999";
}
