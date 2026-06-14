package io.github.openground.base.utils;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * RSA加密工具类
 *
 * @author open-ground
 * @Date: 2026/1/21 20:01
 */
@Slf4j
public class RSAUtil {

    private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;
    private static final Base64.Encoder BASE64_ENCODER = Base64.getEncoder();
    private static final Base64.Decoder BASE64_DECODER = Base64.getDecoder();

    // 固定填充方式（与前端一致）
    private static final String CIPHER_ALGORITHM = "RSA/ECB/PKCS1Padding";
    private static final String RSA_ALGORITHM = "RSA";
    // 密钥长度（2048 位）
    private static final int KEY_SIZE = 2048;

    /**
     * 生成 RSA 密钥对
     *
     * @return Map<String, String>
     * @throws Exception
     */
    public static Map<String, String> generateRSAKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(RSA_ALGORITHM);
        generator.initialize(KEY_SIZE);
        KeyPair keyPair = generator.generateKeyPair();
        String publicKey = BASE64_ENCODER.encodeToString(keyPair.getPublic().getEncoded());
        String privateKey = BASE64_ENCODER.encodeToString(keyPair.getPrivate().getEncoded());
        Map<String, String> keyMap = new HashMap<>();
        keyMap.put("publicKey", publicKey);
        keyMap.put("privateKey", privateKey);
        return keyMap;
    }

    /**
     * RSA加密
     * @param plainText
     * @param publicKeyText
     * @return
     * @throws Exception
     */
    public static String encrypt(String plainText, String publicKeyText) throws Exception {
        PublicKey publicKey = regeneratePublicKey(publicKeyText);
        byte[] plainTextInBytes = plainText.getBytes(DEFAULT_CHARSET);
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        return BASE64_ENCODER.encodeToString(cipher.doFinal(plainTextInBytes));
    }

    /**
     * RSA解密
     *
     * @param ciphertext     密文
     * @param privateKeyText 私钥
     * @return String
     * @throws Exception
     */
    public static String decrypt(String ciphertext, String privateKeyText) throws Exception {
        PrivateKey privateKey = regeneratePrivateKey(privateKeyText);
        byte[] ciphertextInBytes = BASE64_DECODER.decode(ciphertext);
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        return new String(cipher.doFinal(ciphertextInBytes), DEFAULT_CHARSET);
    }

    /**
     * 加载公钥
     *
     * @param publicKeyText 公钥
     * @return PublicKey
     * @throws Exception
     */
    private static PublicKey regeneratePublicKey(String publicKeyText) throws Exception {
        byte[] keyInBytes = BASE64_DECODER.decode(publicKeyText);
        KeyFactory keyFactory = KeyFactory.getInstance(RSA_ALGORITHM);
        // 公钥必须使用RSAPublicKeySpec或者X509EncodedKeySpec
        KeySpec publicKeySpec = new X509EncodedKeySpec(keyInBytes);
        PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);
        return publicKey;
    }

    /**
     * 加载私钥
     *
     * @param key 私钥
     * @return PrivateKey
     * @throws Exception
     */
    private static PrivateKey regeneratePrivateKey(String key) throws Exception {
        byte[] keyInBytes = BASE64_DECODER.decode(key);
        KeyFactory keyFactory = KeyFactory.getInstance(RSA_ALGORITHM);
        // 私钥必须使用RSAPrivateCrtKeySpec或者PKCS8EncodedKeySpec
        KeySpec privateKeySpec = new PKCS8EncodedKeySpec(keyInBytes);
        PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);
        return privateKey;
    }

    /**
     * 获取解密密码
     *
     * @param rsaPrivateKey RSA私钥
     * @param pwd           密码
     * @return String
     */
    public static String getDecryptPwd(String rsaPrivateKey, String pwd) {
        try {
            return decrypt(pwd, rsaPrivateKey);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return null;
        }
    }
}
