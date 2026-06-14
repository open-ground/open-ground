package io.github.openground.base.utils;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.crypto.Mode;
import cn.hutool.crypto.Padding;
import cn.hutool.crypto.symmetric.SM4;
import cn.hutool.crypto.symmetric.SymmetricCrypto;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.Charset;

@Slf4j
public class SM4Utils {

    /**
     * 算法
     */
    private static final Mode MODE = Mode.CBC;
    private static final Padding PADDING = Padding.PKCS5Padding;
    /**
     * 编码方式
     */
    private final static Charset ENCODE = CharsetUtil.CHARSET_UTF_8;


    /**
     * 解密
     * 前台加密后返回的是16进制字符串的base64码，解密时需要反过来
     *
     * @param key 密钥 (需要前端和后端保持一致)十六位作为密钥
     * @param iv 密钥偏移量 (需要前端和后端保持一致)十六位作为密钥偏移量
     * @param encryptStr 待解密的base 64 code
     * @return 解密后的string
     */
    public static String decrypt(String key, String iv, String encryptStr) {
        SymmetricCrypto sm4 = new SM4(MODE, PADDING, key.getBytes(ENCODE), iv.getBytes(CharsetUtil.CHARSET_UTF_8));
        String cipherHex = Base64.decodeStr(encryptStr);
        String plainTxt = sm4.decryptStr(cipherHex, CharsetUtil.CHARSET_UTF_8);

        //排除附加信息
        int len = plainTxt.length() - 13;
        return plainTxt.substring(0, len);
    }

    /**
     * 加密
     * <p>
     * SM4加密后再使用BASE64加密
     *
     * @param key 密钥 (需要前端和后端保持一致)十六位作为密钥
     * @param iv 密钥偏移量 (需要前端和后端保持一致)十六位作为密钥偏移量
     * @param content
     * @return
     */
    public static String encrypt(String key, String iv, String content) {
        content += System.currentTimeMillis();
        SymmetricCrypto sm4 = new SM4(MODE, PADDING, key.getBytes(CharsetUtil.CHARSET_UTF_8), iv.getBytes(CharsetUtil.CHARSET_UTF_8));
        // 返回16进制加密字符串的base64
        String encrypHex = sm4.encryptHex(content);
        String cipherTxt = Base64.encode(encrypHex);
        return cipherTxt;
    }
}
