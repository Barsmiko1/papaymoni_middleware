package com.papaymoni.middleware.util;

import java.security.MessageDigest;

/**
 * Utility class for MD5 hashing
 */
public class EaseIdMd5Util {

    /**
     * Generate MD5 hash (converted to uppercase letters)
     * @param data String to hash
     * @return MD5 hash as a hex string in uppercase
     * @throws Exception if MD5 calculation fails
     */
    public static String MD5(String data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] array = md.digest(data.getBytes("UTF-8"));
        StringBuilder sb = new StringBuilder();
        for (byte item : array) {
            sb.append(Integer.toHexString((item & 0xFF) | 0x100).substring(1, 3));
        }
        return sb.toString().toUpperCase();
    }
}