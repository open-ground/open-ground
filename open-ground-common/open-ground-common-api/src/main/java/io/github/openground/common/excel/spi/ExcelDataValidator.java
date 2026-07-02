package io.github.openground.common.excel.spi;

/**
 * Excel 数据校验器 SPI 接口
 *
 * <p>导入时对特定字段进行自定义校验，返回 null 表示通过，返回字符串表示错误信息。</p>
 *
 * <h3>自定义实现</h3>
 * <pre>{@code
 * public class PhoneValidator implements ExcelDataValidator {
 *     public String validate(Object value, String headerName) {
 *         String phone = (String) value;
 *         if (phone != null && !phone.matches("^1[3-9]\\d{9}$"))
 *             return "手机号格式不正确";
 *         return null;
 *     }
 * }
 * }</pre>
 *
 * @author open-ground
 */
@FunctionalInterface
public interface ExcelDataValidator {

    /**
     * 校验导入字段值
     *
     * @param value      字段值
     * @param headerName 列头名称（用于错误提示）
     * @return null 表示校验通过；非 null 返回错误信息
     */
    String validate(Object value, String headerName);
}
