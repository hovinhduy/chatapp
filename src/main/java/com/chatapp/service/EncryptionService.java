package com.chatapp.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Service để mã hóa và giải mã nội dung tin nhắn
 */
@Service
public class EncryptionService {

    @Value("${app.encryption.key:5v8y/B?E(H+MbQeThWmZq4t6w9z$C&F}")
    private String secretKeyString;

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";

    /**
     * Mã hóa nội dung tin nhắn
     *
     * @param content Nội dung tin nhắn cần mã hóa
     * @return Chuỗi đã được mã hóa
     */
    public String encrypt(String content) {
        try {
            if (content == null || content.isEmpty()) {
                return content;
            }

            SecretKey secretKey = new SecretKeySpec(secretKeyString.substring(0, 16).getBytes(StandardCharsets.UTF_8),
                    ALGORITHM);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encryptedBytes = cipher.doFinal(content.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi mã hóa nội dung tin nhắn", e);
        }
    }

    /**
     * Giải mã nội dung tin nhắn
     *
     * @param encryptedContent Nội dung đã được mã hóa
     * @return Nội dung gốc sau khi giải mã
     */
    public String decrypt(String encryptedContent) {
        try {
            if (encryptedContent == null || encryptedContent.isEmpty()) {
                return encryptedContent;
            }

            SecretKey secretKey = new SecretKeySpec(secretKeyString.substring(0, 16).getBytes(StandardCharsets.UTF_8),
                    ALGORITHM);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decodedBytes = Base64.getDecoder().decode(encryptedContent);
            byte[] decryptedBytes = cipher.doFinal(decodedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi giải mã nội dung tin nhắn", e);
        }
    }
}