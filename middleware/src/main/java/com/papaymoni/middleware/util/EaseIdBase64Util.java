package com.papaymoni.middleware.util;

import java.util.Base64;

/**
 * Utility class for Base64 encoding and decoding
 */
public class EaseIdBase64Util {

    /**
     * Decode Base64 string to byte array
     * @param base64 Base64 encoded string
     * @return Decoded byte array
     */
    public static byte[] decode(String base64) {
        return decode(base64.getBytes());
    }

    /**
     * Decode Base64 bytes to byte array
     * @param bytes Base64 encoded bytes
     * @return Decoded byte array
     */
    public static byte[] decode(byte[] bytes) {
        return Base64.getDecoder().decode(bytes);
    }

    /**
     * Encode byte array to Base64 string
     * @param bytes Byte array to encode
     * @return Base64 encoded string
     */
    public static String encode(byte[] bytes) {
        return new String(Base64.getEncoder().encode(bytes));
    }

    /**
     * Encode string to Base64 string
     * @param data String to encode
     * @return Base64 encoded string
     */
    public static String encode(String data){
        if (data == null){
            return null;
        }
        return encode(data.getBytes());
    }
}