package io.github.openground.common.excel.spi;

import java.util.List;
import java.util.Map;

/**
 * Excel 自定义查询/落库提供者 SPI 接口
 *
 * <p>当 {@code @ExcelTemplate(queryType = CUSTOM)} 时，框架会调用此接口的实现。</p>
 *
 * <h3>自定义实现</h3>
 * <pre>{@code
 * @Component
 * public class UserExcelProvider implements ExcelQueryProvider {
 *     public List<?> queryExportData(Map<String, Object> params) {
 *         return userService.listByCondition(params);
 *     }
 *     public void handleImport(List<?> data, Map<String, Object> params) {
 *         for (Object row : data) {
 *             userService.saveOrUpdate((UserExcelVO) row);
 *         }
 *     }
 * }
 * }</pre>
 *
 * @author open-ground
 */
public interface ExcelQueryProvider {

    /**
     * 查询导出数据
     *
     * @param params 查询参数（由请求端传入）
     * @return 导出数据列表
     */
    List<?> queryExportData(Map<String, Object> params);

    /**
     * 处理导入数据（落库）
     * <p>TABLE 模式下框架会自动 INSERT，CUSTOM 模式下由实现者决定落库逻辑。</p>
     *
     * @param data   解析后的数据列表
     * @param params 导入参数（由请求端传入）
     */
    default void handleImport(List<?> data, Map<String, Object> params) {
        throw new UnsupportedOperationException("handleImport not implemented");
    }
}
