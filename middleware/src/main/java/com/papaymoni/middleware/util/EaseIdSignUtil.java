package com.papaymoni.middleware.util;

import java.util.*;

/**
 * Utility class for signing EaseID API requests
 * Based on PalmPay's implementation pattern for correct signature generation
 */
public class EaseIdSignUtil {

    // Define fields that need to be signed if specific fields are required
    private static final List<String> MERCHANTNEEDSIGNFILED = Arrays.asList();
    private static final List<String> OTHERSNEEDSIGNFILED = Arrays.asList();

    public enum SignType {
        RSA, HMACSHA256
    }

    /**
     * Generate a signature for the request data
     *
     * @param data Map of parameters to sign
     * @param privateKey RSA private key in Base64 format
     * @param signType Type of signature (RSA or HMACSHA256)
     * @return Base64 encoded signature
     * @throws Exception if signature generation fails
     */
    public static String generateSign(final Map<String, Object> data, String privateKey, SignType signType) throws Exception {
        Map<String, String> map = new HashMap<>();
        Set<String> set = data.keySet();

        if (SignType.RSA.equals(signType)) {
            // For RSA, include all fields (no filtering)
            for (String key : set) {
                map.put(key, String.valueOf(data.get(key)));
            }
        } else {
            // For HMACSHA256, only include specified fields if defined
            for (String key : set) {
                if (OTHERSNEEDSIGNFILED.contains(key)) {
                    map.put(key, String.valueOf(data.get(key)));
                }
            }
        }

        return generateSignature(map, privateKey, signType);
    }

    /**
     * Generate signature from the prepared parameter map
     *
     * @param data Map of parameters to sign
     * @param privateKey RSA private key in Base64 format or HMAC secret key
     * @param signType Type of signature (RSA or HMACSHA256)
     * @return Encoded signature
     * @throws Exception if signature generation fails
     */
    private static String generateSignature(final Map<String, String> data, String privateKey, SignType signType) throws Exception {
        // Get sorted parameter string
        String sortedStr = sortStr(data);

        // Generate MD5 hash of the sorted string - critical step!
        String md5Content = EaseIdMd5Util.MD5(sortedStr);

        // Log for debugging
        System.out.println("Parameter string: " + sortedStr);
        System.out.println("MD5 hash: " + md5Content);

        if (SignType.RSA.equals(signType)) {
            // Sign the MD5 hash with RSA
            return EaseIdRsaUtil.sign(md5Content, privateKey);
        } else {
            // Sign with HMACSHA256
            return EaseIdRsaUtil.HMACSHA256(md5Content, privateKey);
        }
    }

    /**
     * Sort parameters alphabetically and format as key=value&key2=value2
     * Critical for correct signature generation
     *
     * @param data Map of parameters to sort and format
     * @return Formatted string of parameters
     */
    private static String sortStr(Map<String, String> data) {
        Set<String> keySet = data.keySet();
        String[] keyArray = keySet.toArray(new String[keySet.size()]);
        Arrays.sort(keyArray);

        StringBuilder sb = new StringBuilder();
        for (String k : keyArray) {
            if (data.get(k).trim().length() > 0) {
                // Only include non-empty values
                sb.append(k).append("=").append(data.get(k).trim()).append("&");
            }
        }

        // Remove the trailing '&'
        if (sb.length() > 0) {
            return sb.substring(0, sb.length() - 1);
        }

        return "";
    }

    /**
     * Verify a signature
     *
     * @param data Map of parameters that were signed
     * @param publicKey RSA public key in Base64 format
     * @param sign Signature to verify
     * @param signType Type of signature (RSA or HMACSHA256)
     * @return true if signature is valid, false otherwise
     * @throws Exception if verification fails
     */
    public static boolean verifySign(final Map<String, Object> data, String publicKey, String sign, SignType signType) throws Exception {
        Map<String, String> map = new HashMap<>();
        Set<String> set = data.keySet();

        if (SignType.RSA.equals(signType)) {
            for (String key : set) {
                map.put(key, String.valueOf(data.get(key)));
            }
        } else {
            for (String key : set) {
                if (OTHERSNEEDSIGNFILED.contains(key)) {
                    map.put(key, String.valueOf(data.get(key)));
                }
            }
        }

        String sortedStr = sortStr(map);
        String md5Content = EaseIdMd5Util.MD5(sortedStr);

        if (SignType.RSA.equals(signType)) {
            return EaseIdRsaUtil.verify(md5Content, publicKey, sign);
        } else {
            return EaseIdRsaUtil.HMACSHA256(md5Content, publicKey).equals(sign);
        }
    }
}