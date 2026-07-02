package io.github.openground.common.excel.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Excel 导入单行错误信息
 *
 * @author open-ground
 */
public class ImportRowError {

    /** 出错行号（从 1 开始，含表头） */
    private int rowNum;

    /** 错误消息 */
    private String message;

    /** 该行的原始数据（headerName → value），用于生成错误 Excel */
    private Map<String, Object> rowData = new LinkedHashMap<>();

    public ImportRowError() {}

    public ImportRowError(int rowNum, String message) {
        this.rowNum = rowNum;
        this.message = message;
    }

    public ImportRowError(int rowNum, String message, Map<String, Object> rowData) {
        this.rowNum = rowNum;
        this.message = message;
        this.rowData = rowData;
    }

    public int getRowNum() { return rowNum; }
    public void setRowNum(int rowNum) { this.rowNum = rowNum; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Map<String, Object> getRowData() { return rowData; }
    public void setRowData(Map<String, Object> rowData) { this.rowData = rowData; }
}
