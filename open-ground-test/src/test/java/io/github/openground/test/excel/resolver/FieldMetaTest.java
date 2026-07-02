package io.github.openground.test.excel.resolver;

import io.github.openground.common.excel.resolver.FieldMeta;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FieldMeta 单元测试
 */
class FieldMetaTest {

    @Test
    void testHasDictType() {
        FieldMeta withDict = FieldMeta.builder()
                .fieldName("gender")
                .headerName("性别")
                .dictType("gender")
                .build();
        assertTrue(withDict.hasDictType());

        FieldMeta withoutDict = FieldMeta.builder()
                .fieldName("username")
                .headerName("用户名")
                .dictType("")
                .build();
        assertFalse(withoutDict.hasDictType());

        FieldMeta nullDict = FieldMeta.builder()
                .fieldName("email")
                .headerName("邮箱")
                .build();
        assertFalse(nullDict.hasDictType());
    }

    @Test
    void testHasDateFormat() {
        FieldMeta withFormat = FieldMeta.builder()
                .fieldName("createTime")
                .headerName("创建时间")
                .dateFormat("yyyy-MM-dd")
                .build();
        assertTrue(withFormat.hasDateFormat());

        FieldMeta withoutFormat = FieldMeta.builder()
                .fieldName("username")
                .headerName("用户名")
                .dateFormat("")
                .build();
        assertFalse(withoutFormat.hasDateFormat());
    }

    @Test
    void testFieldMetaBuilder() {
        FieldMeta meta = FieldMeta.builder()
                .fieldName("phone")
                .headerName("手机号")
                .order(3)
                .required(true)
                .exportIgnore(false)
                .importIgnore(false)
                .fieldType(String.class)
                .build();

        assertEquals("phone", meta.getFieldName());
        assertEquals("手机号", meta.getHeaderName());
        assertEquals(3, meta.getOrder());
        assertTrue(meta.isRequired());
        assertFalse(meta.isExportIgnore());
        assertEquals(String.class, meta.getFieldType());
    }
}
