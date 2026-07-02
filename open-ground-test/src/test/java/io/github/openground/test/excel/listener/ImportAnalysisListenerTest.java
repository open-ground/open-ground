package io.github.openground.test.excel.listener;

import io.github.openground.test.excel.TestUserVO;
import io.github.openground.common.excel.listener.ImportAnalysisListener;
import io.github.openground.common.excel.model.ImportResult;
import io.github.openground.common.excel.model.ImportRowError;
import io.github.openground.common.excel.resolver.ExcelAnnotationResolver;
import io.github.openground.common.excel.resolver.FieldMeta;
import io.github.openground.common.excel.spi.DictTranslator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ImportAnalysisListener 单元测试（模拟 EasyExcel 回调）
 */
class ImportAnalysisListenerTest {

    private ImportAnalysisListener<TestUserVO> listener;
    private ExcelAnnotationResolver resolver;
    private List<FieldMeta> fields;

    @BeforeEach
    void setUp() {
        resolver = new ExcelAnnotationResolver();
        fields = resolver.getImportFields(TestUserVO.class);
        DictTranslator noopTranslator = (dictType, value) -> value;
        listener = new ImportAnalysisListener<>(TestUserVO.class, fields, noopTranslator, resolver);
    }

    @Test
    void testValidRow() {
        // 模拟一条有效数据
        TestUserVO vo = new TestUserVO("zhangsan", "张三", "0", "test@test.com", null, 1);
        listener.invoke(vo, null);
        listener.doAfterAllAnalysed(null);

        ImportResult result = listener.getResult();
        assertEquals(1, result.getSuccessRows());
        assertEquals(0, result.getFailRows());
        assertEquals(1, listener.getValidRows().size());
        assertTrue(result.isAllSuccess());
    }

    @Test
    void testInvalidRowMissingRequired() {
        // 模拟缺少必填字段
        TestUserVO vo = new TestUserVO(null, "张三", "0", null, null, 1);
        listener.invoke(vo, null);
        listener.doAfterAllAnalysed(null);

        ImportResult result = listener.getResult();
        assertEquals(0, result.getSuccessRows());
        assertEquals(1, result.getFailRows());
        assertTrue(listener.getValidRows().isEmpty());

        ImportRowError error = result.getErrors().get(0);
        assertTrue(error.getMessage().contains("用户名") || error.getMessage().contains("邮箱"));
    }

    @Test
    void testMultipleRows() {
        // 有效行
        listener.invoke(new TestUserVO("zhangsan", "张三", "0", "a@a.com", null, 1), null);
        // 无效行（缺少必填）
        listener.invoke(new TestUserVO(null, "李四", "1", "b@b.com", null, 1), null);
        // 有效行
        listener.invoke(new TestUserVO("wangwu", "王五", "0", "c@c.com", null, 1), null);
        listener.doAfterAllAnalysed(null);

        ImportResult result = listener.getResult();
        assertEquals(2, result.getSuccessRows());
        assertEquals(1, result.getFailRows());
        assertEquals(2, listener.getValidRows().size());
    }

    @Test
    void testErrorRowDataCaptured() {
        // 模拟一条有错误的数据，验证 rowData 被捕获
        TestUserVO vo = new TestUserVO(null, "张三", "0", "test@test.com", null, 1);
        listener.invoke(vo, null);
        listener.doAfterAllAnalysed(null);

        ImportRowError error = listener.getResult().getErrors().get(0);
        assertNotNull(error.getRowData());
        assertFalse(error.getRowData().isEmpty());

        // rowData 应该包含所有字段的值
        assertEquals("张三", error.getRowData().get("昵称"));
        assertEquals("0", error.getRowData().get("性别"));
    }

    @Test
    void testDictTranslationDuringImport() {
        // 使用一个将 "男" → "0" 的字典翻译器
        DictTranslator genderTranslator = (dictType, value) -> {
            if ("gender".equals(dictType) && "男".equals(value)) return "0";
            if ("gender".equals(dictType) && "女".equals(value)) return "1";
            return value;
        };

        ImportAnalysisListener<TestUserVO> translatorListener =
                new ImportAnalysisListener<>(TestUserVO.class, fields, genderTranslator, resolver);

        // 模拟导入时传入 label "男"，应翻译为 code "0"
        TestUserVO vo = new TestUserVO("zhangsan", "张三", "男", "a@a.com", null, 1);
        translatorListener.invoke(vo, null);
        translatorListener.doAfterAllAnalysed(null);

        TestUserVO saved = translatorListener.getValidRows().get(0);
        assertEquals("0", saved.getGender());
    }
}
