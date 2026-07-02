package io.github.openground.test.excel.resolver;

import io.github.openground.test.excel.TestUserVO;
import io.github.openground.common.excel.resolver.ExcelAnnotationResolver;
import io.github.openground.common.excel.resolver.FieldMeta;
import io.github.openground.common.excel.annotation.ExcelTemplate;
import io.github.openground.common.excel.enums.QueryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ExcelAnnotationResolver 单元测试
 */
class ExcelAnnotationResolverTest {

    private ExcelAnnotationResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ExcelAnnotationResolver();
    }

    @Test
    void testGetTemplate() {
        ExcelTemplate template = resolver.getTemplate(TestUserVO.class);
        assertNotNull(template);
        assertEquals("sys_test_user", template.tableName());
        assertEquals("用户数据", template.sheetName());
        assertEquals(QueryType.TABLE, template.queryType());
        assertTrue(template.needHead());
    }

    @Test
    void testGetExportFields() {
        List<FieldMeta> fields = resolver.getExportFields(TestUserVO.class);
        assertNotNull(fields);

        // 应排除 exportIgnore=true 的 status 字段
        // TestUserVO 有 username(1), nickname(2), gender(3), email(4), createTime(5), status(6 exportIgnore)
        // 所以导出应有 5 个字段
        assertEquals(5, fields.size());

        // 验证顺序
        assertEquals("username", fields.get(0).getFieldName());
        assertEquals("nickname", fields.get(1).getFieldName());
        assertEquals("gender", fields.get(2).getFieldName());
        assertEquals("email", fields.get(3).getFieldName());
        assertEquals("createTime", fields.get(4).getFieldName());

        // 验证属性
        FieldMeta emailField = fields.get(3);
        assertEquals("email", emailField.getFieldName());
        assertEquals("邮箱", emailField.getHeaderName());
        assertTrue(emailField.isRequired());
    }

    @Test
    void testGetImportFields() {
        List<FieldMeta> fields = resolver.getImportFields(TestUserVO.class);
        assertNotNull(fields);

        // 导入包含所有非 importIgnore 的字段（TestUserVO 没有 importIgnore 字段）
        assertEquals(6, fields.size());

        // 验证 required 属性
        assertTrue(fields.get(0).isRequired()); // username
        assertFalse(fields.get(1).isRequired()); // nickname
        assertTrue(fields.get(3).isRequired());  // email
    }

    @Test
    void testGetFields() {
        List<FieldMeta> fields = resolver.getFields(TestUserVO.class);
        assertEquals(6, fields.size());

        // 验证字典类型
        Optional<FieldMeta> genderField = fields.stream()
                .filter(f -> "gender".equals(f.getFieldName()))
                .findFirst();
        assertTrue(genderField.isPresent());
        assertEquals("gender", genderField.get().getDictType());

        // 验证日期格式
        Optional<FieldMeta> createTimeField = fields.stream()
                .filter(f -> "createTime".equals(f.getFieldName()))
                .findFirst();
        assertTrue(createTimeField.isPresent());
        assertEquals("yyyy-MM-dd", createTimeField.get().getDateFormat());
    }

    @Test
    void testGetFieldByHeader() {
        Optional<FieldMeta> field = resolver.getFieldByHeader(TestUserVO.class, "手机号");
        assertTrue(field.isPresent());
        assertEquals("createTime", field.get().getFieldName()); // 手机号映射到 createTime
    }

    @Test
    void testGetFieldByHeaderNotFound() {
        Optional<FieldMeta> field = resolver.getFieldByHeader(TestUserVO.class, "不存在的列");
        assertFalse(field.isPresent());
    }

    @Test
    void testCaching() {
        // 多次调用应返回缓存结果
        List<FieldMeta> first = resolver.getFields(TestUserVO.class);
        List<FieldMeta> second = resolver.getFields(TestUserVO.class);
        assertSame(first, second); // 同一对象引用

        ExcelTemplate t1 = resolver.getTemplate(TestUserVO.class);
        ExcelTemplate t2 = resolver.getTemplate(TestUserVO.class);
        assertSame(t1, t2);
    }
}
