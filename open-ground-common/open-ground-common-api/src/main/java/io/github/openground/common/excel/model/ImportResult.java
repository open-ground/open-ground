package io.github.openground.common.excel.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Excel 导入结果
 *
 * @author open-ground
 */
public class ImportResult {

    /** 总行数（不含表头） */
    private int totalRows;

    /** 成功行数 */
    private int successRows;

    /** 失败行数 */
    private int failRows;

    /** 错误详情 */
    private List<ImportRowError> errors = new ArrayList<>();

    public ImportResult() {}

    public ImportResult(int totalRows, int successRows, int failRows, List<ImportRowError> errors) {
        this.totalRows = totalRows;
        this.successRows = successRows;
        this.failRows = failRows;
        this.errors = errors;
    }

    public int getTotalRows() { return totalRows; }
    public void setTotalRows(int totalRows) { this.totalRows = totalRows; }

    public int getSuccessRows() { return successRows; }
    public void setSuccessRows(int successRows) { this.successRows = successRows; }

    public int getFailRows() { return failRows; }
    public void setFailRows(int failRows) { this.failRows = failRows; }

    public List<ImportRowError> getErrors() { return errors; }
    public void setErrors(List<ImportRowError> errors) { this.errors = errors; }

    public void addError(ImportRowError error) {
        this.errors.add(error);
        this.failRows++;
    }

    public void incrementSuccess() {
        this.successRows++;
    }

    public boolean isAllSuccess() {
        return failRows == 0;
    }
}
