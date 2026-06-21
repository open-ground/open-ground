package io.github.openground.base.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 统一响应结果对象
 *
 * @param <T> 数据类型
 * @author open-ground
 */
@Accessors(chain = true)
@Getter
@Setter
@NoArgsConstructor
@ToString
@SuppressWarnings("all")
@Schema(description = "统一响应结果对象")
public class CommonResult<T> implements Serializable {
    private static final long serialVersionUID = 6191745064790884707L;

    @Schema(description = "响应码，0000 表示成功", example = "0000")
    private String code;
    @Schema(description = "响应消息")
    private String message;
    @Schema(description = "响应数据")
    private T data;

    public CommonResult(String code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 操作成功
     */
    public static <T> CommonResult<T> success(T data) {
        return new CommonResult<T>()
                .setCode("0000")
                .setMessage("Success")
                .setData(data);
    }

    /**
     * 操作成功（无返回数据）
     */
    public static <T> CommonResult<T> success() {
        return success(null);
    }

    /**
     * 操作失败
     */
    public static <T> CommonResult<T> error(String code, String message) {
        return new CommonResult<T>()
                .setCode(code)
                .setMessage(message);
    }

    /**
     * 操作失败（带数据）
     */
    public static <T> CommonResult<T> error(String code, String message, T data) {
        return new CommonResult<T>()
                .setCode(code)
                .setMessage(message)
                .setData(data);
    }
}
