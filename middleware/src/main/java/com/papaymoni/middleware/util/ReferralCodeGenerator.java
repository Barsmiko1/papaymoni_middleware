package com.papaymoni.middleware.util;

import java.security.SecureRandom;
import java.util.Base64;

public class ReferralCodeGenerator {

    private static final SecureRandom random = new SecureRandom();
    private static final int CODE_LENGTH = 8;

    public static String generateReferralCode() {
        byte[] bytes = new byte[CODE_LENGTH];
        random.nextBytes(bytes);

        // Convert to Base64 and remove non-alphanumeric characters
        String code = Base64.getEncoder().encodeToString(bytes)
                .replaceAll("[^a-zA-Z0-9]", "")
                .substring(0, CODE_LENGTH);

        return code.toUpperCase();
    }
}
