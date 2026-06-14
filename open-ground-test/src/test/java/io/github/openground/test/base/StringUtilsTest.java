package io.github.openground.test.base;

import io.github.openground.base.utils.StringUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StringUtils 工具类测试
 *
 * @author open-ground
 */
@DisplayName("StringUtils 测试")
class StringUtilsTest {

    @Nested
    @DisplayName("isEquals")
    class IsEquals {

        @Test
        @DisplayName("相同字符串应返回 true")
        void shouldReturnTrueForEqual() {
            assertTrue(StringUtils.isEquals("hello", "hello"));
        }

        @Test
        @DisplayName("不同字符串应返回 false")
        void shouldReturnFalseForDifferent() {
            assertFalse(StringUtils.isEquals("hello", "world"));
        }

        @Test
        @DisplayName("src 为 null 应返回 false")
        void shouldReturnFalseWhenSrcNull() {
            assertFalse(StringUtils.isEquals(null, "hello"));
        }

        @Test
        @DisplayName("两个都为 null 应返回 false")
        void shouldReturnFalseWhenBothNull() {
            assertFalse(StringUtils.isEquals(null, null));
        }
    }

    @Nested
    @DisplayName("isBlank / isNotBlank")
    class IsBlank {

        @Test
        @DisplayName("null 应判定为 blank")
        void nullShouldBeBlank() {
            assertTrue(StringUtils.isBlank(null));
        }

        @Test
        @DisplayName("空字符串应判定为 blank")
        void emptyStringShouldBeBlank() {
            assertTrue(StringUtils.isBlank(""));
        }

        @Test
        @DisplayName("空白字符串应判定为 blank")
        void whitespaceShouldBeBlank() {
            assertTrue(StringUtils.isBlank("   "));
            assertTrue(StringUtils.isBlank("\t\n"));
        }

        @Test
        @DisplayName("非空字符串应判定为非 blank")
        void nonEmptyShouldNotBeBlank() {
            assertFalse(StringUtils.isBlank("hello"));
        }

        @Test
        @DisplayName("isNotBlank 应与 isBlank 相反")
        void isNotBlankShouldBeOpposite() {
            assertTrue(StringUtils.isNotBlank("hello"));
            assertFalse(StringUtils.isNotBlank(""));
            assertFalse(StringUtils.isNotBlank(null));
        }
    }

    @Nested
    @DisplayName("isNumeric")
    class IsNumeric {

        @Test
        @DisplayName("纯数字应返回 true")
        void shouldReturnTrueForNumeric() {
            assertTrue(StringUtils.isNumeric("12345"));
        }

        @Test
        @DisplayName("含非数字字符应返回 false")
        void shouldReturnFalseForNonNumeric() {
            assertFalse(StringUtils.isNumeric("123a45"));
            assertFalse(StringUtils.isNumeric("abc"));
        }

        @Test
        @DisplayName("null 应返回 false")
        void shouldReturnFalseForNull() {
            assertFalse(StringUtils.isNumeric(null));
        }
    }

    @Nested
    @DisplayName("nullToEmpty / trimToEmpty")
    class NullToEmpty {

        @Test
        @DisplayName("null 应转换为空字符串")
        void shouldConvertNullToEmpty() {
            assertEquals("", StringUtils.nullToEmpty(null));
        }

        @Test
        @DisplayName("非 null 应保持原值")
        void shouldKeepNonNull() {
            assertEquals("hello", StringUtils.nullToEmpty("hello"));
        }

        @Test
        @DisplayName("trimToEmpty 应去除首尾空格")
        void shouldTrimToEmpty() {
            assertEquals("hello", StringUtils.trimToEmpty("  hello  "));
            assertEquals("", StringUtils.trimToEmpty(null));
        }
    }
}
