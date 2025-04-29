package com.papaymoni.middleware.util;

import java.io.FileOutputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

/**
 * Utility class for generating RSA key pairs
 * Run this as a standalone application to generate keys for BVN verification
 */
public class RsaKeyGenerator {

    public static void main(String[] args) throws Exception {
        // Generate key pair
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(1024); // Use 1024 bits as required
        KeyPair keyPair = keyGen.generateKeyPair();

        // Get public and private keys
        PublicKey publicKey = keyPair.getPublic();
        PrivateKey privateKey = keyPair.getPrivate();

        // Base64 encode keys
        String publicKeyBase64 = Base64.getEncoder().encodeToString(publicKey.getEncoded());
        String privateKeyBase64 = Base64.getEncoder().encodeToString(privateKey.getEncoded());

        // Print keys
        System.out.println("Public Key (Upload to BVN Verification Provider):");
        System.out.println(publicKeyBase64);
        System.out.println("\nPrivate Key (Add to application.properties as bvn.verification.private.key):");
        System.out.println(privateKeyBase64);

        // Save keys to files
        try (FileOutputStream publicOut = new FileOutputStream("bvn_verification_public.key");
             FileOutputStream privateOut = new FileOutputStream("bvn_verification_private.key")) {

            publicOut.write(publicKeyBase64.getBytes());
            privateOut.write(privateKeyBase64.getBytes());
        }

        System.out.println("\nKeys also saved to files: bvn_verification_public.key and bvn_verification_private.key");
    }
}