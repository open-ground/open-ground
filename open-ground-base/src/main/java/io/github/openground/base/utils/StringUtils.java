package io.github.openground.base.utils;

import java.io.File;
import java.util.Arrays;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 字符串的帮助类
 *
 * @author open-ground
 * @version 2.0.0
 * @since 2.0.0
 */
@SuppressWarnings("all")
public final class StringUtils {

  private static final char[] ILLEGAL_FILES_CHARACTER = new char[]{'\\', '/', ':', '*', '?', '<', '>', '|', '"'};
  private static Pattern pattern = Pattern.compile("(?<!_)[A-Z]");

  /**
   * 判断两个字符串是否相等，若两个字符串同时为null，也不认为是相等的。
   *
   * @param src 字符串
   * @param dest 字符串
   * @return 相等返回true，否则返回false。
   */
  public static boolean isEquals(String src, String dest) {
    return src != null && src.equals(dest);
  }

  /**
   * 判断两个字符串是否相等，若字符串同时为null,则认为相等。
   *
   * @param src 字符串
   * @param dest 字符串
   * @return 相等或者null返回true，否则返回false。
   */
  public static boolean isEqualsIgnoredNull(String src, String dest) {
    return src == null ? dest == null : src.equals(dest);
  }

  /**
   * 判断给定的值是否是空白的，空白的内容包括：null、""、" "；
   *
   * @param value 要判断的值
   * @return 空白返回true，否则返回false
   */
  public static boolean isBlank(String value) {
    return value == null || value.trim().length() == 0;
  }

  /**
   * 判断给定的值是否是空白的，空白的内容包括：null、""、" "；
   *
   * @param value 要判断的值
   * @return 空白返回true，否则返回false
   */
  public static boolean isNotBlank(String value) {
    return value != null && value.trim().length() > 0;
  }

  /**
   * 首字母大写，同时若value中包含空格，则会忽略空格。
   *
   * @param value 内容
   * @return 格式化内容
   */
  public static String formatAndCapitalizeFirstLetter(String value) {
    char[] chars = value.toCharArray();
    char[] newChars = new char[chars.length];
    int newCharsIndex = 0;

    for (int i = 0; i < chars.length; i++) {
      if (Character.isSpaceChar(chars[i])) {
        continue;
      }
      if (newCharsIndex == 0 && Character.isLowerCase(chars[i])) {
        chars[i] = Character.toUpperCase(chars[i]);
      }
      if (newCharsIndex > 0 && Character.isUpperCase(chars[i])) {
        chars[i] = Character.toLowerCase(chars[i]);
      }
      newChars[newCharsIndex++] = chars[i];
    }
    return String.valueOf(Arrays.copyOf(newChars, newCharsIndex));
  }

  /**
   * 仅仅转换首字母为大写
   *
   * @param value 需转换的内容
   * @return 首字母大写
   */
  public static String upperCaseFirstLetter(String value) {
    char[] chars = value.toCharArray();

    if (Character.isLowerCase(chars[0])) {
      chars[0] = Character.toUpperCase(chars[0]);
    }
    return String.valueOf(chars);
  }

  /**
   * 替换包名中的.为File.separator
   *
   * @param pkg 包名
   * @return 转换后的内容
   */
  public static String packageToDirectory(String pkg, String delimiter) {
    StringTokenizer tokenizer = new StringTokenizer(pkg, delimiter);
    StringBuilder builder = new StringBuilder();

    while (tokenizer.hasMoreTokens()) {
      builder.append(tokenizer.nextToken()).append(File.separator);
    }
    return builder.toString();
  }


  /**
   * 首字母转换为小写
   *
   * @param value 转换内容
   * @return 首字母小写
   */
  public static String lowerCaseFirstLetter(String value) {
    char[] chars = value.toCharArray();

    if (Character.isUpperCase(chars[0])) {
      chars[0] = Character.toLowerCase(chars[0]);
    }
    return String.valueOf(chars);
  }

  /**
   * 把Null的value转换为空字符串
   *
   * @param value 值
   * @return "" 或者 value
   */
  public static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }


  public static boolean contains(String src, String... values) {
    int valueLength = 0;

    for (String value : values) {
      valueLength += value.length();
    }
    if (src.length() < valueLength) {
      return false;
    }
    char[] sources = src.toCharArray();
    int offset = 0;

    for (String value : values) {
      offset = indexAfterValue(sources, offset, value);

      if (offset == -1) {
        return false;
      }
    }
    return true;
  }

  public static boolean startWithAny(String src, String... values) {
    int min = Byte.MAX_VALUE;

    for (String value : values) {
      min = Math.min(min, value.length());
    }
    if (src.length() < min) {
      return false;
    }
    char[] sources = src.toCharArray();
    int offset = 0;
    char[] destChars;

    label:
    for (String value : values) {
      destChars = value.toCharArray();

      if (sources[offset] != destChars[offset]) {
        continue;
      }
      for (int i = 0; i < destChars.length; i++) {
        if (sources[i] != destChars[i]) {
          break label;
        }
      }
      return true;
    }
    return false;
  }

  @SuppressWarnings("all")
  static int indexAfterValue(char[] sources, int offset, String value) {
    char[] dest = value.toCharArray();
    int sourceLength = sources.length;
    int destLength = dest.length;

    if (sourceLength - offset < destLength) {
      return -1;
    }
    char first = dest[0];

    for (; offset < sourceLength; offset++) {
      if (sources[offset] != first) {
        while (++offset < sourceLength && sources[offset] != first) {
          ;
        }
      }
      if (offset == sourceLength || sourceLength - offset < destLength) {
        return -1;
      }
      int i = 1;
      for (; i < destLength && sources[offset + i] == dest[i]; i++) {
        ;
      }
      if (i == destLength) {
        return offset + i;
      }
    }
    return -1;
  }

  public static String replaceAll(String src, String replacement, char... chars) {
    if (chars.length == 0 || src == null) {
      return src;
    }
    char[] sources = src.toCharArray();
    StringBuilder builder = new StringBuilder();

    for (char source : sources) {
      if (contains(chars, source)) {
        builder.append(replacement);
        continue;
      }
      builder.append(source);
    }
    return builder.toString();
  }

  public static String trimAll(String value) {
    return replaceAll(value, "", ' ');
  }

  private static boolean contains(char[] chars, char source) {
    for (char c : chars) {
      if (c == source) {
        return true;
      }
    }
    return false;
  }


  /**
   * 下划线转驼峰法(默认小驼峰)
   *
   * @param name 源字符串
   * @return 转换后的字符串
   */
  public static String convertToHump(String name) {
    if (!name.contains("_")) {
      if (Character.isUpperCase(name.charAt(0))) {
        return name.toLowerCase();
      }
      return name;
    }
    char[] chars = name.toCharArray();
    char[] newChars = new char[chars.length];
    int count = 0;

    for (int i = 0; i < chars.length; i++) {
      if (chars[i] == '_') {
        newChars[count++] = Character.toUpperCase(chars[++i]);
        continue;
      }
      newChars[count++] = Character.toLowerCase(chars[i]);
    }
    return String.valueOf(newChars, 0, count);
  }

  /**
   * 小驼峰转大写下划线形式
   *
   * @param value 需转换字符串
   * @return 转换后字符串
   */
  public static String hump2Upper(String value) {
    if (value == null) {
      return null;
    }
    String str = value.replaceAll("_", "");

    if (isAllUpperCase(str)) {
      return value;
    }
    Matcher matcher = pattern.matcher(value);
    if (matcher.find()) {
      return matcher.replaceAll("_$0").toUpperCase();
    }
    return value.toUpperCase();
  }

  /**
   * @param str 字符串
   * @return 是否全大写
   */
  public static boolean isAllUpperCase(String str) {
    for (int i = 0; i < str.length(); i++) {
      char c = str.charAt(i);
      if (c >= 97 && c <= 122) {
        return false;
      }
    }
    return true;
  }


  @SuppressWarnings("all")
  public static boolean isIntString(String value) {
    boolean result = true;
    if (isBlank(value)) {
      result = false;
    }
    try {
      Integer.parseInt(value);
    } catch (Throwable t) {
      result = false;
    }
    return result;
  }

  /**
   * 判断给定的值中是否包含Window操作系统中，非法的文件名字符
   *
   * @param name 文件名
   * @return 包含非法字符，返回true，否则返回false。
   */
  public static boolean hasIllegalWinFileCharacter(String name) {
    if (isBlank(name)) {
      return false;
    }
    char[] chars = name.toCharArray();

    for (char c : chars) {
      for (char i : ILLEGAL_FILES_CHARACTER) {
        if (i == c) {
          return true;
        }
      }
    }
    return false;

  }

  public static String rationalizeName(String name) {
    if (isBlank(name)) {
      return null;
    }
    if (!hasIllegalWinFileCharacter(name)) {
      return name;
    }
    char[] chars = name.toCharArray();
    StringBuilder result = new StringBuilder();

    for (char c : chars) {
      for (char i : ILLEGAL_FILES_CHARACTER) {
        if (i == c) {
          c = '_';
          break;
        }
      }
      result.append(c);
    }
    return result.toString();
  }

  public static boolean isNumeric(String str) {
    if (str == null) {
      return false;
    } else {
      int sz = str.length();

      for (int i = 0; i < sz; ++i) {
        if (!Character.isDigit(str.charAt(i))) {
          return false;
        }
      }
      return true;
    }
  }

  public static String trimToEmptyStr(String str){
    return str == null ? "" : str.trim();
  }


  public static String convertToLike(String obj) {
    return "%" + obj + "%";
  }

  /**
   * 按字节截取字符串长度，遇到不完整的中文则自动舍弃
   *
   * @param str      源字符串
   * @param length   目标字节长度
   * @param encoding 字符编码（如 "UTF-8"）
   * @return 截取后的字符串
   */
  public static String subByBytes(String str, int length, String encoding) {
    if (str == null) return "";
    try {
      String subStr = str.substring(0, Math.min(str.length(), length));
      int subStrBytesL = subStr.getBytes(encoding).length;
      while (subStrBytesL > length) {
        length--;
        subStr = str.substring(0, Math.min(length, str.length()));
        subStrBytesL = subStr.getBytes(encoding).length;
      }
      return subStr;
    } catch (Exception e) {
      return str;
    }
  }

  public static boolean isEmptyAfterTrim(String str) {
    return StringUtils.isBlank(StringUtils.trimToEmpty(str));
  }


  public static String trimToEmpty(String str) {
    return str == null ? "" : str.trim();
  }

  public static boolean isNotEmptyAfterTrim(String str) {
    return !isEmptyAfterTrim(str);
  }
}
