package io.github.openground.base.utils;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

/**
 * MyBatis-Plus 条件构建工具
 *
 * <p>将 DO 对象中非 null/非空字段自动转换为 WHERE 条件。</p>
 */
public final class MpConditions {

    private MpConditions() {
    }

    /**
     * 将 DO 对象转为 QueryWrapper，非 null/非空字段作为 WHERE 条件
     *
     * <pre>
     * // 示例：按 company + spaceNo 删除
     * FpsAmtSpaceDO where = new FpsAmtSpaceDO();
     * where.setCompany("000000");
     * where.setSpaceNo("SPACE01");
     * mapper.delete(MpConditions.whereOf(where));
     * </pre>
     *
     * @param doObj DO 对象
     * @return QueryWrapper
     */
    public static <T> QueryWrapper<T> whereOf(T doObj) {
        return new QueryWrapper<>(doObj);
    }
}
