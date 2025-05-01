package com.papaymoni.middleware.service;

/**
 * Service for encrypting and decrypting sensitive data
 */
public interface EncryptionService {

    /**
     * Encrypt data
     * @param data String to encrypt
     * @return Encrypted string with "ENC:" prefix
     */
    String encrypt(String data);

    /**
     * Decrypt data
     * @param encryptedData Encrypted string (with or without "ENC:" prefix)
     * @return Decrypted string
     */
    String decrypt(String encryptedData);
}