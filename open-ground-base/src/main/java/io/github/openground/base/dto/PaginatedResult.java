package io.github.openground.base.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 分页响应结果对象
 *
 * @param <T> 分页数据类型
 * @author open-ground
 */
@Accessors(chain = true)
@Getter
@Setter
@ToString
@NoArgsConstructor
@SuppressWarnings("all")
public class PaginatedResult<T> extends CommonResult<T> {
    private static final long serialVersionUID = 6191745064790884707L;

    private int currentPage; // Current page number
    private int totalPage; // Number of total pages
    private Long totalCount; // Number of total items

    /**
     * 构建分页成功结果
     */
    public static <T> PaginatedResult<T> success(T data, int currentPage, int totalPage, Long totalCount) {
        PaginatedResult<T> result = new PaginatedResult<T>();
        result.setCode("0000");
        result.setMessage("Success");
        result.setData(data);
        result.setCurrentPage(currentPage);
        result.setTotalPage(totalPage);
        result.setTotalCount(totalCount);
        return result;
    }

    /**
     * 构建分页成功结果（基于 totalCount 自动计算 totalPage）
     */
    public static <T> PaginatedResult<T> success(T data, int currentPage, Long totalCount, int perPage) {
        int totalPage = (int) (totalCount / perPage + (totalCount % perPage == 0 ? 0 : 1));
        return success(data, currentPage, totalPage, totalCount);
    }

    // ============ 重写父类 setter 返回 PaginatedResult 以支持链式调用 ============

    @Override
    public PaginatedResult<T> setCode(String code) {
        super.setCode(code);
        return this;
    }

    @Override
    public PaginatedResult<T> setMessage(String message) {
        super.setMessage(message);
        return this;
    }

    @Override
    public PaginatedResult<T> setData(T data) {
        super.setData(data);
        return this;
    }
}
