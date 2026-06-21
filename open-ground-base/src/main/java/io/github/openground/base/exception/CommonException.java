package io.github.openground.base.exception;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Optional;

/**
 * @author open-ground
 */
@Accessors(chain = true)
@Setter
@Getter
@SuppressWarnings("all")
@Schema(description = "业务异常，包含错误码和错误消息")
public class CommonException extends RuntimeException{

    private static final long serialVersionUID = 2565431806475335331L;

    @Schema(description = "错误码")
    private String code;

    @Schema(description = "错误消息")
    private String msg;

    public CommonException() {}

    public CommonException(String code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    @Override
    public String getMessage() {
        return Optional.ofNullable(msg).orElse(code);
    }
}
