package io.github.openground.base.utils;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;

/**
 * 金额工具类
 *
 * @author open-ground
 * @version 1.0
 */
@Slf4j
public class AmtUtil {
    /** 金额单位：亿元 */
    public static final BigDecimal UNIT = new BigDecimal(10000);

    /**
     * 将 null 或无效值转为 BigDecimal.ZERO
     *
     * @param amt 金额
     * @return 非 null 的 BigDecimal
     */
    public static BigDecimal null2Zero(BigDecimal amt) {
        if (amt == null || "".equals(amt.toString()) || "null".equals(amt.toString()) || "undefined".equals(amt.toString())) {
            return new BigDecimal("0.00");
        }
        return amt;
    }

    /**
     * 转换为亿元（保留2位小数，四舍五入）
     *
     * @param amt 金额
     * @return 亿元金额
     */
    public static BigDecimal divNotNull(BigDecimal amt) {
        return null2Zero(amt).divide(UNIT, 2, RoundingMode.HALF_UP);
    }

    /**
     * 格式化金额保留小数（四舍五入）
     *
     * @param amt 入参金额
     * @param num 小数点位数
     * @return 格式化后的金额
     */
    public static BigDecimal formatAmt(BigDecimal amt, int num) {
        if (amt == null) {
            if (log.isErrorEnabled()) {
                log.error("【AmtUtil.formatAmt】输入金额为空");
            }
            StringBuilder sb = new StringBuilder();
            sb.append(0);
            for (int i = 0; i < num; i++) {
                if (i == 0) {
                    sb.append(".0");
                } else {
                    sb.append("0");
                }
            }
            amt = new BigDecimal(sb.toString());
        }
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < num; i++) {
            if (i == 0) {
                sb.append(".0");
            } else {
                sb.append("0");
            }
        }
        DecimalFormat df = new DecimalFormat(sb.toString());
        df.setRoundingMode(RoundingMode.HALF_UP);
        return new BigDecimal(df.format(amt));
    }

    /**
     * 两个数字相加
     *
     * @param a   数字 a
     * @param b   数字 b
     * @param num 保留小数位数（四舍五入）
     * @return 相加结果
     */
    public static BigDecimal add(BigDecimal a, BigDecimal b, int num) {
        return a.add(b).setScale(num, RoundingMode.HALF_UP);
    }

    /**
     * 两个数字相减
     *
     * @param a   数字 a
     * @param b   数字 b
     * @param num 保留小数位数（四舍五入）
     * @return 相减结果
     */
    public static BigDecimal sub(BigDecimal a, BigDecimal b, int num) {
        return a.subtract(b).setScale(num, RoundingMode.HALF_UP);
    }

    /**
     * 两个数字相乘
     *
     * @param a   数字 a
     * @param b   数字 b
     * @param num 保留小数位数（四舍五入）
     * @return 相乘结果
     */
    public static BigDecimal mul(BigDecimal a, BigDecimal b, int num) {
        return a.multiply(b).setScale(num, RoundingMode.HALF_UP);
    }

    /**
     * 两个数字相除
     *
     * @param a   数字 a
     * @param b   数字 b
     * @param num 保留小数位数（四舍五入）
     * @return 相除结果
     */
    public static BigDecimal exc(BigDecimal a, BigDecimal b, int num) {
        return a.divide(b, num, RoundingMode.HALF_UP);
    }

    /**
     * 两个数字求商
     *
     * @param a   数字 a
     * @param b   数字 b
     * @param num 保留小数位数（四舍五入）
     * @return 商
     */
    public static BigDecimal quotient(BigDecimal a, BigDecimal b, int num) {
        BigDecimal[] results = a.divideAndRemainder(b);
        return results[0].setScale(num, RoundingMode.HALF_UP);
    }

    /**
     * 两个数字求余
     *
     * @param a   数字 a
     * @param b   数字 b
     * @param num 保留小数位数（四舍五入）
     * @return 余数
     */
    public static BigDecimal more(BigDecimal a, BigDecimal b, int num) {
        BigDecimal[] results = a.divideAndRemainder(b);
        return results[1].setScale(num, RoundingMode.HALF_UP);
    }
}
