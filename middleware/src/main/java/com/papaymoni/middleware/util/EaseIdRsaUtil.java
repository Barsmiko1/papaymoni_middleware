//package com.papaymoni.middleware.util;
//
//import lombok.extern.slf4j.Slf4j;
//import org.apache.logging.log4j.util.Base64Util;
//
//import javax.crypto.Mac;
//import javax.crypto.spec.SecretKeySpec;
//import java.nio.charset.StandardCharsets;
//import java.security.KeyFactory;
//import java.security.MessageDigest;
//import java.security.PrivateKey;
//import java.security.Signature;
//import java.security.spec.PKCS8EncodedKeySpec;
//import java.util.Base64;
//
///**
// * Utility class for RSA operations including signature generation
// */
//@Slf4j
//public class RsaUtil {
//
//    private static final String RSA = "RSA";
//    private static final String MD5_WITH_RSA = "MD5withRSA";
//    private static final String HMAC_SHA256 = "HmacSHA256";
//    private static final String UTF8 = "UTF-8";
//
//    /**
//     * Sign data using RSA private key
//     *
//     * @param data Data to sign
//     * @param privateKey Private key in Base64 format
//     * @return Base64 encoded signature
//     * @throws Exception If signing fails
//     */
//    public static String signs(String data, String privateKey) throws Exception {
//        // Calculate MD5 hash of the data
//        String md5Data = md5Hex(data);
//        log.debug("md5: {}", md5Data);
//
//        // Use the MD5 hash to generate signature
//        PrivateKey priKey = getPrivateKeyFromPKCS8(RSA, privateKey);
//        Signature signature = Signature.getInstance(MD5_WITH_RSA);
//        signature.initSign(priKey);
//        signature.update(md5Data.getBytes(StandardCharsets.UTF_8));
//        byte[] signed = signature.sign();
//
//        return Base64.getEncoder().encodeToString(signed);
//    }
//
//    public static PrivateKey getPrivateKey(String privateKey) throws Exception {
//        byte[] keyBytes = Base64UtilEaseID.decode(privateKey.getBytes());
//        PKCS8EncodedKeySpec pkcs8KeySpec = new PKCS8EncodedKeySpec(keyBytes);
//        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
//        PrivateKey privateK = keyFactory.generatePrivate(pkcs8KeySpec);
//        return privateK;
//    }
//
//    public static String sign(String encryData, String privateKey) throws Exception {
//        byte[] data = encryData.getBytes();
//        PrivateKey privateK = getPrivateKey(privateKey);
//        Signature signature = Signature.getInstance("RSA");
//        signature.initSign(privateK);
//        signature.update(data);
//        String signatureString = Base64UtilEaseID.encode(signature.sign());
//        log.debug("Signature String: {}", signatureString);
//
//        return Base64UtilEaseID.encode(signature.sign());
//    }
//
//    /**
//     * Sign data using HMAC-SHA256
//     *
//     * @param data Data to sign
//     * @param key Secret key for HMAC
//     * @return Base64 encoded HMAC
//     * @throws Exception If signing fails
//     */
//    public static String HMACSHA256(String data, String key) throws Exception {
//        Mac sha256_HMAC = Mac.getInstance(HMAC_SHA256);
//        SecretKeySpec secret_key = new SecretKeySpec(key.getBytes(UTF8), HMAC_SHA256);
//        sha256_HMAC.init(secret_key);
//        byte[] array = sha256_HMAC.doFinal(data.getBytes(UTF8));
//        StringBuilder sb = new StringBuilder();
//        for (byte item : array) {
//            sb.append(Integer.toHexString((item & 0xFF) | 0x100).substring(1, 3));
//        }
//        return sb.toString().toUpperCase();
//    }
//
//    /**
//     * Get private key from PKCS8 encoded key string
//     *
//     * @param algorithm Key algorithm (e.g., RSA)
//     * @param privateKey Base64 encoded private key
//     * @return PrivateKey object
//     * @throws Exception If key parsing fails
//     */
//    private static PrivateKey getPrivateKeyFromPKCS8(String algorithm, String privateKey) throws Exception {
//        byte[] keyBytes = Base64.getDecoder().decode(privateKey);
//        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
//        KeyFactory keyFactory = KeyFactory.getInstance(algorithm);
//        return keyFactory.generatePrivate(keySpec);
//    }
//
//    /**
//     * Calculate MD5 hash of input string
//     *
//     * @param input String to hash
//     * @return MD5 hash as a hex string
//     * @throws Exception If MD5 calculation fails
//     */
//    private static String md5Hex(String input) throws Exception {
//        MessageDigest md = MessageDigest.getInstance("MD5");
//        byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
//        return bytesToHex(digest);
//    }
//
//    /**
//     * Convert bytes to hexadecimal string
//     *
//     * @param bytes Byte array to convert
//     * @return Hex string
//     */
//    private static String bytesToHex(byte[] bytes) {
//        StringBuilder sb = new StringBuilder();
//        for (byte b : bytes) {
//            String hex = Integer.toHexString(0xff & b);
//            if (hex.length() == 1) {
//                sb.append('0');
//            }
//            sb.append(hex);
//        }
//        return sb.toString();
//    }
//}



package com.papaymoni.middleware.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * Utility class for RSA operations including signature generation
 */
public class EaseIdRsaUtil {

    /**
     * RSA algorithm name
     */
    private static final String KEY_ALGORITHM = "RSA";

    /**
     * Signature algorithm - Changed to SHA1withRSA to match PalmPay's implementation
     */
    private static final String SIGNATURE_ALGORITHM = "SHA1WithRSA";

    /**
     * RSA encryption mode
     */
    private static final String RSA_ALGORITHM = "RSA/ECB/PKCS1Padding";

    /**
     * UTF-8 charset name
     */
    private static final String UTF8 = "UTF-8";

    /**
     * Sign data using RSA private key
     *
     * @param data Data to sign (should be MD5 hash of parameters)
     * @param privateKey Private key in Base64 format
     * @return Base64 encoded signature
     * @throws Exception If signing fails
     */
    public static String sign(String data, String privateKey) throws Exception {
        byte[] dataBytes = data.getBytes();
        PrivateKey privateK = getPrivateKey(privateKey);
        Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
        signature.initSign(privateK);
        signature.update(dataBytes);
        return EaseIdBase64Util.encode(signature.sign());
    }

    /**
     * Sign data using HMAC-SHA256
     *
     * @param data Data to sign
     * @param key Secret key for HMAC
     * @return Base64 encoded HMAC
     * @throws Exception If signing fails
     */
    public static String HMACSHA256(String data, String key) throws Exception {
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec(key.getBytes(UTF8), "HmacSHA256");
        sha256_HMAC.init(secret_key);
        byte[] array = sha256_HMAC.doFinal(data.getBytes(UTF8));
        StringBuilder sb = new StringBuilder();
        for (byte item : array) {
            sb.append(Integer.toHexString((item & 0xFF) | 0x100).substring(1, 3));
        }
        return sb.toString().toUpperCase();
    }

    /**
     * Verify signature
     *
     * @param data Data that was signed
     * @param publicKey Public key in Base64 format
     * @param sign Signature to verify
     * @return true if signature is valid, false otherwise
     * @throws Exception If verification fails
     */
    public static boolean verify(String data, String publicKey, String sign) throws Exception {
        byte[] dataBytes = data.getBytes();
        PublicKey publicK = getPublicKey(publicKey);
        Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
        signature.initVerify(publicK);
        signature.update(dataBytes);
        byte[] signBytes = EaseIdBase64Util.decode(sign);
        return signature.verify(signBytes);
    }

    /**
     * Get private key from Base64 encoded string
     *
     * @param privateKey Base64 encoded private key
     * @return PrivateKey object
     * @throws Exception If key parsing fails
     */
    private static PrivateKey getPrivateKey(String privateKey) throws Exception {
        byte[] keyBytes = EaseIdBase64Util.decode(privateKey);
        PKCS8EncodedKeySpec pkcs8KeySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
        return keyFactory.generatePrivate(pkcs8KeySpec);
    }

    /**
     * Get public key from Base64 encoded string
     *
     * @param publicKey Base64 encoded public key
     * @return PublicKey object
     * @throws Exception If key parsing fails
     */
    private static PublicKey getPublicKey(String publicKey) throws Exception {
        byte[] keyBytes = EaseIdBase64Util.decode(publicKey);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
        return keyFactory.generatePublic(keySpec);
    }
}