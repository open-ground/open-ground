package io.github.openground.test.excel.model;

import io.github.openground.common.excel.model.ImportResult;
import io.github.openground.common.excel.model.ImportRowError;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ImportResult 单元测试
 */
class ImportResultTest {

    @Test
    void testEmptyResult() {
        ImportResult result = new ImportResult();
        assertEquals(0, result.getTotalRows());
        assertEquals(0, result.getSuccessRows());
        assertEquals(0, result.getFailRows());
        assertTrue(result.getErrors().isEmpty());
        assertTrue(result.isAllSuccess());
    }

    @Test
    void testAddError() {
        ImportResult result = new ImportResult();
        result.addError(new ImportRowError(2, "用户名 为必填项"));
        result.addError(new ImportRowError(3, "邮箱 格式不正确"));

        assertEquals(2, result.getFailRows());
        assertEquals(2, result.getErrors().size());
        assertFalse(result.isAllSuccess());
        assertEquals(2, result.getErrors().get(0).getRowNum());
        assertEquals("邮箱 格式不正确", result.getErrors().get(1).getMessage());
    }

    @Test
    void testIncrementSuccess() {
        ImportResult result = new ImportResult();
        result.incrementSuccess();
        result.incrementSuccess();
        result.incrementSuccess();

        assertEquals(3, result.getSuccessRows());
        assertTrue(result.isAllSuccess());
    }

    @Test
    void testMixedResult() {
        ImportResult result = new ImportResult();
        result.incrementSuccess();
        result.incrementSuccess();
        result.addError(new ImportRowError(3, "手机号 格式不正确"));

        result.setTotalRows(3);
        assertEquals(3, result.getTotalRows());
        assertEquals(2, result.getSuccessRows());
        assertEquals(1, result.getFailRows());
        assertFalse(result.isAllSuccess());
    }

    @Test
    void testImportRowErrorWithRowData() {
        Map<String, Object> rowData = new LinkedHashMap<>();
        rowData.put("用户名", "zhangsan");
        rowData.put("邮箱", "bad-email");

        ImportRowError error = new ImportRowError(2, "邮箱 格式不正确", rowData);

        assertEquals(2, error.getRowNum());
        assertEquals("邮箱 格式不正确", error.getMessage());
        assertEquals("zhangsan", error.getRowData().get("用户名"));
        assertEquals("bad-email", error.getRowData().get("邮箱"));
    }
}
