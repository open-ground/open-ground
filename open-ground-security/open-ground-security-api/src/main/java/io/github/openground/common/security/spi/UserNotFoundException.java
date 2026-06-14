package io.github.openground.common.security.spi;

import io.github.openground.base.constant.ErrorCode;
import io.github.openground.base.exception.CommonException;

/**
 * 用户未找到异常
 *
 * @author open-ground
 * @version 1.0
 */
public class UserNotFoundException extends CommonException {

    public UserNotFoundException(String username) {
        super(ErrorCode.RESOURCE_NOT_FOUND_ERROR, "User not found: " + username);
    }
}