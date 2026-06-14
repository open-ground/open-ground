package io.github.openground.base.utils;

import cn.hutool.core.util.StrUtil;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * <p>Description: AES 加密解密工具类</P>
 * <p>使用 Java 标准库实现，无第三方依赖</p>
 *
 * @author open-ground
 * @Version 3.0.0
 */
public class AESUtil {

    /**
     * AES 加密
     *
     * @param key           密钥
     * @param iv            偏移量
     * @param plainTextData 明文数据
     * @return 加密后的 Base64 字符串
     */
    public static String encrypt(String key, String iv, String plainTextData) throws Exception {
        Cipher cipher = buildCipher(key, iv, Cipher.ENCRYPT_MODE);
        byte[] encryptedData = cipher.doFinal(plainTextData.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encryptedData);
    }

    /**
     * AES 解密
     *
     * @param key           密钥
     * @param iv            偏移量
     * @param encryptedData Base64 编码的密文
     * @return 解密后的明文字符串，解密失败返回 null
     */
    public static String decrypt(String key, String iv, String encryptedData) {
        try {
            Cipher cipher = buildCipher(key, iv, Cipher.DECRYPT_MODE);
            byte[] decryptedData = cipher.doFinal(Base64.getDecoder().decode(encryptedData));
            return new String(decryptedData, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static Cipher buildCipher(String key, String iv, int cipherMode) throws Exception {
        byte[] keyRaw = key.getBytes(StandardCharsets.UTF_8);
        byte[] ivRaw = iv.getBytes(StandardCharsets.UTF_8);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        IvParameterSpec ivParameterSpec = new IvParameterSpec(ivRaw);
        cipher.init(cipherMode, new SecretKeySpec(keyRaw, "AES"), ivParameterSpec);
        return cipher;
    }

    /**
     * 先使用固定 key,iv 值解密，无法解密时，使用配置 key,iv 解密。
     * 用于登录用户密码修改场景。
     * <p>临时解决方案，后续登录改造时删除。</p>
     *
     * @param key 配置密钥
     * @param iv  配置偏移量
     * @param pwd 待解密的密文
     * @return 解密后的密码
     */
    public static String getDecryptPwd(String key, String iv, String pwd) {
        String decrypt = decryptDefault(pwd);
        if (StrUtil.isEmpty(decrypt)) {
            decrypt = decrypt(key, iv, pwd);
        }
        return decrypt;
    }

    /**
     * 固定 key、iv 值解密，用于登录用户密码修改。
     * <p>临时解决方案，后续登录改造时删除。</p>
     */
    private static String decryptDefault(String encryptedData) {
        try {
            Cipher cipher = buildCipher("ABCDEFG123456KEY", "ABCDEFG1234567IV", Cipher.DECRYPT_MODE);
            byte[] decryptedData = cipher.doFinal(Base64.getDecoder().decode(encryptedData));
            String word = new String(decryptedData, StandardCharsets.UTF_8);
            return word.substring(0, word.length() - 13);
        } catch (Exception e) {
            return null;
        }
    }

}
