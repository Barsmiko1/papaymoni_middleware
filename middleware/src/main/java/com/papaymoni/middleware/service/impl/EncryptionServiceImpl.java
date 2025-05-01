package com.papaymoni.middleware.service.impl;

import com.papaymoni.middleware.service.EncryptionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;

@Service
@Slf4j
public class EncryptionServiceImpl implements EncryptionService {

    @Value("${app.encryption.secret}")
    private String encryptionSecret;

    @Value("${app.encryption.salt}")
    private String encryptionSalt;

    private SecretKey secretKey;
    private Cipher cipher;

    @PostConstruct
    public void init() throws Exception {
        try {
            // Generate a strong key using PBKDF2
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            KeySpec spec = new PBEKeySpec(
                    encryptionSecret.toCharArray(),
                    encryptionSalt.getBytes(),
                    65536,
                    256
            );
            secretKey = new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
            cipher = Cipher.getInstance("AES/GCM/NoPadding");

            log.info("Encryption service initialized successfully");
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            log.error("Failed to initialize encryption service", e);
            throw new RuntimeException("Failed to initialize encryption service", e);
        }
    }

    @Override
    public String encrypt(String data) {
        try {
            // Generate a random 12-byte IV (Initialization Vector)
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);

            // Initialize cipher for encryption
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            // Encrypt the data
            byte[] encryptedData = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));

            // Combine IV and encrypted data
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + encryptedData.length);
            byteBuffer.put(iv);
            byteBuffer.put(encryptedData);

            // Return as Base64 string with "ENC:" prefix for easy identification
            return "ENC:" + Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            log.error("Error encrypting data", e);
            throw new RuntimeException("Error encrypting data", e);
        }
    }

    @Override
    public String decrypt(String encryptedData) {
        try {
            // Check if the data has the "ENC:" prefix and remove it
            if (encryptedData.startsWith("ENC:")) {
                encryptedData = encryptedData.substring(4);
            }

            // Decode from Base64
            byte[] decoded = Base64.getDecoder().decode(encryptedData);
            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);

            // Extract IV
            byte[] iv = new byte[12];
            byteBuffer.get(iv);

            // Extract ciphertext
            byte[] cipherText = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherText);

            // Initialize cipher for decryption
            GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            // Decrypt the data
            byte[] decryptedData = cipher.doFinal(cipherText);
            return new String(decryptedData, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Error decrypting data", e);
            throw new RuntimeException("Error decrypting data", e);
        }
    }
}