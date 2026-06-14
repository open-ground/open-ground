package io.github.openground.base.utils;

import cn.hutool.crypto.digest.MD5;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * @ClassName SignUtil
 * @Description 数字签名工具
 * @author open-ground
 * @Date 2022/8/19 10:01
 * @Version 1.0
 */
public class SignUtil {
    private SignUtil() throws Exception {
        throw new Exception("工具类，禁止实例化");
    }

    /**
     * 验签
     * @param sign 签名
     * @param data 数据
     * @param key 密钥 (需要前端和后端保持一致)十六位作为密钥
     * @param iv 密钥偏移量 (需要前端和后端保持一致)十六位作为密钥偏移量
     * @return
     */
    public static int verify(String sign, String data, String key, String iv) {
        // 签名SM4解密
        String decrypt = SM4Utils.decrypt(key, iv, sign);
        // 数据md5
        String digest = digest(data);
        return MessageDigest.isEqual(decrypt.getBytes(),digest.getBytes()) ? 1 : 0;
    }

    /**
     * 验签
     *
     * @param sign      签名
     * @param data      数据
     * @param timestamp 时间戳
     * @param expire    过期时间
     * @param key 密钥 (需要前端和后端保持一致)十六位作为密钥
     * @param iv 密钥偏移量 (需要前端和后端保持一致)十六位作为密钥偏移量
     * @return 验证结果
     */
    public static int verify(String sign, String data, long timestamp, long expire, String key, String iv) {
        long now = System.currentTimeMillis();
        if (now - timestamp > expire) {
            // 签名已过期
            return -1;
        }
        return verify(sign, data, key, iv);
    }

    /**
     * 数据摘要
     *
     * @param data 数据
     * @return 摘要
     */
    private static String digest(String data) {
        return new MD5().digestHex(data, StandardCharsets.UTF_8);
    }
}
