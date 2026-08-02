package io.github.openground.common.excel;

import io.github.openground.common.excel.model.ImportResult;
import javax.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * Excel 导入导出核心服务接口
 *
 * @author open-ground
 */
public interface ExcelService {

    /**
     * 导出 Excel（输出到 HttpServletResponse）
     *
     * @param voClass  VO 类（需标注 @ExcelTemplate、@ExcelField）
     * @param params   查询参数
     * @param response HttpServletResponse
     */
    void export(Class<?> voClass, Map<String, Object> params, HttpServletResponse response);

    /**
     * 导出 Excel（输出到 OutputStream）
     *
     * @param voClass VO 类
     * @param params  查询参数
     * @param os      输出流
     */
    void export(Class<?> voClass, Map<String, Object> params, OutputStream os);

    /**
     * 导出 Excel（直接传入数据列表，不查询数据库）
     *
     * @param voClass VO 类
     * @param data    数据列表
     * @param os      输出流
     */
    void exportWithData(Class<?> voClass, List<?> data, OutputStream os);

    /**
     * 导入 Excel
     *
     * @param voClass VO 类
     * @param file    上传的 Excel 文件
     * @param params  导入参数
     * @param <T>     泛型
     * @return 导入结果
     */
    <T> ImportResult importExcel(Class<T> voClass, MultipartFile file, Map<String, Object> params);

    /**
     * 下载导入模板（仅表头，无数据行）
     *
     * @param voClass  VO 类
     * @param response HttpServletResponse
     */
    void downloadTemplate(Class<?> voClass, HttpServletResponse response);

    /**
     * 导入 Excel，当存在错误时将错误信息写入 Excel 响应流
     * <p>生成的 Excel 包含：原始数据列 + "错误原因" 列，仅含校验失败的行。</p>
     *
     * @param voClass  VO 类
     * @param file     上传的 Excel 文件
     * @param params   导入参数
     * @param response HTTP 响应（写入错误 Excel 或 JSON）
     */
    void importAndWriteErrorFile(Class<?> voClass, MultipartFile file,
                                  Map<String, Object> params, HttpServletResponse response);
}
