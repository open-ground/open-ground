package io.github.openground.base.exception;

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
public class CommonException extends RuntimeException{

    private static final long serialVersionUID = 2565431806475335331L;

    private String code;

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
