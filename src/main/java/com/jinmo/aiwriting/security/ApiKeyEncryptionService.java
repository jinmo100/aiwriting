package com.jinmo.aiwriting.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * API Key 应用层加密服务。
 *
 * <p>使用 AES-GCM。密钥来源于配置；dev 可明文，prod 应使用环境变量。</p>
 */
@Service
public class ApiKeyEncryptionService {

    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;

    private final SecretKeySpec keySpec;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiKeyEncryptionService(
        @Value("${aiwriting.security.encryption-key:dev-only-aiwriting-secret-change-me}") String encryptionKey
    ) {
        this.keySpec = new SecretKeySpec(deriveAesKey(encryptionKey), KEY_ALGORITHM);
    }

    public String encrypt(String plainText) {
        if (!StringUtils.hasText(plainText)) {
            throw new IllegalArgumentException("API Key 不能为空");
        }

        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
            buffer.put(iv);
            buffer.put(encrypted);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new IllegalStateException("加密 API Key 失败", e);
        }
    }

    public String decrypt(String cipherText) {
        if (!StringUtils.hasText(cipherText)) {
            throw new IllegalArgumentException("无法解密 API Key：密文为空");
        }

        try {
            byte[] payload = Base64.getUrlDecoder().decode(cipherText);
            if (payload.length <= IV_BYTES) {
                throw new IllegalArgumentException("密文长度非法");
            }

            byte[] iv = new byte[IV_BYTES];
            byte[] encrypted = new byte[payload.length - IV_BYTES];
            System.arraycopy(payload, 0, iv, 0, IV_BYTES);
            System.arraycopy(payload, IV_BYTES, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalArgumentException("无法解密 API Key", e);
        }
    }

    private static byte[] deriveAesKey(String encryptionKey) {
        if (!StringUtils.hasText(encryptionKey)) {
            throw new IllegalArgumentException("加密主密钥不能为空");
        }
        try {
            return MessageDigest.getInstance("SHA-256")
                .digest(encryptionKey.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("初始化加密主密钥失败", e);
        }
    }
}
