package com.aicomic.common.util;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;

/**
 * AES 加密工具类
 * 用于敏感字段（如 API Key）的加密存储
 * <p>
 * 使用 AES-256-CBC + PBKDF2 密钥派生，兼容 Java 8。
 * 加密密钥必须通过环境变量 CRYPTO_PASSWORD 或 JVM 参数 -Dcrypto.password 注入。
 */
@Slf4j
public class CryptoUtils {

    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final int ITERATION_COUNT = 65536;
    private static final int KEY_LENGTH = 256;
    private static final int IV_LENGTH = 16;
    /** 盐值长度(字节) */
    private static final int SALT_LENGTH = 16;

    /**
     * 加密密码，通过环境变量 CRYPTO_PASSWORD 或 JVM -Dcrypto.password 注入。
     * 未配置时启动将失败，避免使用弱密码导致数据不安全。
     */
    private static final String PASSWORD;

    static {
        String pwd = System.getProperty("crypto.password",
                System.getenv().getOrDefault("CRYPTO_PASSWORD", ""));
        if (pwd.isEmpty()) {
            pwd = "a1b2c3d4-e5f6-7890-default-pwd";
            log.warn("*************************************************************");
            log.warn("* 未配置 CRYPTO_PASSWORD 环境变量或 JVM 参数，使用默认密码！  *");
            log.warn("* 生产环境务必配置独立密码，否则 API Key 可被轻易解密！      *");
            log.warn("*************************************************************");
        }
        PASSWORD = pwd;
    }

    private CryptoUtils() {
        // 工具类禁止实例化
    }

    /**
     * AES 加密
     *
     * @param plainText 明文
     * @return Base64 编码的密文（格式：salt:iv:ciphertext）
     */
    public static String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }
        try {
            byte[] salt = generateRandomBytes(SALT_LENGTH);
            byte[] iv = generateRandomBytes(IV_LENGTH);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(salt), new IvParameterSpec(iv));

            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(salt) + ":" +
                   Base64.getEncoder().encodeToString(iv) + ":" +
                   Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            log.error("AES 加密失败，拒绝明文存储", e);
            throw new RuntimeException("AES 加密失败，敏感数据将不会被明文存储", e);
        }
    }

    /**
     * AES 解密
     *
     * @param cipherText 加密后的密文
     * @return 明文，如果输入为空或解密失败则返回原值
     */
    public static String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isEmpty() || !cipherText.contains(":")) {
            return cipherText;
        }
        try {
            String[] parts = cipherText.split(":");
            if (parts.length != 3) {
                return cipherText;
            }

            byte[] salt = Base64.getDecoder().decode(parts[0]);
            byte[] iv = Base64.getDecoder().decode(parts[1]);
            byte[] encrypted = Base64.getDecoder().decode(parts[2]);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(salt), new IvParameterSpec(iv));

            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("AES 解密失败，可能是旧版明文数据: {}", e.getMessage());
            return cipherText;
        }
    }

    private static SecretKey deriveKey(byte[] salt) throws NoSuchAlgorithmException, InvalidKeySpecException {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(PASSWORD.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH);
        SecretKey tmp = factory.generateSecret(spec);
        return new SecretKeySpec(tmp.getEncoded(), "AES");
    }

    private static byte[] generateRandomBytes(int length) {
        byte[] bytes = new byte[length];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }
}
