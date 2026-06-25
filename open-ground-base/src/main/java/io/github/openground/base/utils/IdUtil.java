package io.github.openground.base.utils;

import org.springframework.util.StringUtils;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * @author jack.zhang
 * @date 2023/7/19
 */
@SuppressWarnings("all")
public class IdUtil {
  public static final Pattern APP_ID_CHECK = Pattern.compile("[\\u4e00-\\u9fa5]");

  public static String getUUID() {
    return UUID.randomUUID().toString().replace("-", "");
  }

  public static String getMD5String(String str) {
    if (!StringUtils.hasText(str)) {
      return null;
    }
    byte[] secretBytes;
    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      md.update(str.getBytes());
      secretBytes = md.digest();
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("No such algorithm MD5");
    }
    String md5code = new BigInteger(1, secretBytes).toString(16);// 16进制数字
    for (int i = 0; i < 32 - md5code.length(); i++) {
      md5code = "0" + md5code;
    }
    return md5code;
  }

  /**
   * 检查字符串中是否存在汉字
   *
   * @param appId
   * @return 若存在汉字返回true, 否则返回false
   */
  public static boolean appIdCheck(String appId) {
    return APP_ID_CHECK.matcher(appId).find();
  }

}
