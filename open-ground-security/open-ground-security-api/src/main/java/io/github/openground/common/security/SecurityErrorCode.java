package io.github.openground.common.security;

import io.github.openground.base.constant.ErrorCode;

/**
 * Security 模块错误码
 *
 * <p>定义认证授权模块专用错误码，扩展自 {@link ErrorCode}。
 *
 * <h3>错误码区间</h3>
 * <ul>
 *   <li>{@code 1400-1499} — API Key 相关错误</li>
 * </ul>
 *
 * @author open-ground
 */
public interface SecurityErrorCode extends ErrorCode {

    /** API Key 不存在 */
    String API_KEY_NOT_FOUND = "1400";

    /** API Key 已过期 */
    String API_KEY_EXPIRED = "1401";

    /** API Key 已被禁用 */
    String API_KEY_DISABLED = "1402";

    /** API Key 已存在 */
    String API_KEY_ALREADY_EXISTS = "1403";

    /** API Key 生成失败 */
    String API_KEY_GENERATE_ERROR = "1404";

    /** API Key 超过数量限制 */
    String API_KEY_LIMIT_EXCEEDED = "1405";
}
