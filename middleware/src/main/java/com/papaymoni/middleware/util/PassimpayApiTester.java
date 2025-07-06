package com.papaymoni.middleware.util;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * Standalone class to test Passimpay API directly
 * This class doesn't depend on any internal code and can be run independently
 */
public class PassimpayApiTester {

    // API configuration
    private static final String API_URL = "https://api.passimpay.io/v2/address";

    // Replace these with your actual credentials
    private static String platformId = "1608"; // Your platform ID
    private static String apiKey = "ab4d29-16e57b-d11dbf-36d45d-b68a31"; // Your API key

    public static void main(String[] args) {
        try {
            Scanner scanner = new Scanner(System.in);

            // Get API credentials if not set
            if (apiKey.equals("ab4d29-16e57b-d11dbf-36d45d-b68a31")) {
                System.out.print("Enter your Passimpay Platform ID: ");
                platformId = scanner.nextLine().trim();

                System.out.print("Enter your Passimpay API Key: ");
                apiKey = scanner.nextLine().trim();
            }

            // Get currency and payment ID
            System.out.println("\nSelect currency to test:");
            System.out.println("1. BTC (Payment ID: 10)");
            System.out.println("2. ETH (Payment ID: 11)");
            System.out.println("3. USDT on Ethereum (Payment ID: 12)");
            System.out.println("4. USDT on Tron (Payment ID: 21)");
            System.out.print("Enter your choice (1-4): ");

            int choice = Integer.parseInt(scanner.nextLine().trim());
            int paymentId;
            String currencyName;

            switch (choice) {
                case 1:
                    paymentId = 10;
                    currencyName = "BTC";
                    break;
                case 2:
                    paymentId = 11;
                    currencyName = "ETH";
                    break;
                case 3:
                    paymentId = 12;
                    currencyName = "USDT (ERC20)";
                    break;
                case 4:
                    paymentId = 21;
                    currencyName = "USDT (TRC20)";
                    break;
                default:
                    paymentId = 10;
                    currencyName = "BTC";
                    break;
            }

            System.out.println("\nTesting with currency: " + currencyName + " (Payment ID: " + paymentId + ")");

            // Generate a valid order ID (using only allowed characters: A-Za-z0-9+/=-:.,)
            String orderId = "test-order-" + System.currentTimeMillis();

            // Create request JSON
            String requestJson = String.format(
                    "{\"platformId\":\"%s\",\"paymentId\":%d,\"orderId\":\"%s\"}",
                    platformId, paymentId, orderId
            );

            System.out.println("\nRequest JSON: " + requestJson);

            // Calculate signature
            String signature = calculateSignature(platformId, requestJson, apiKey);
            System.out.println("Generated Signature: " + signature);

            // Send request to API
            String response = sendRequest(API_URL, requestJson, signature);

            // Print response
            System.out.println("\nAPI Response: " + response);

            // Parse and explain response
            if (response.contains("\"result\":1")) {
                System.out.println("\n✅ SUCCESS: Wallet address created successfully!");
            } else if (response.contains("\"result\":0")) {
                System.out.println("\n❌ ERROR: Failed to create wallet address.");

                // Try to extract error message
                if (response.contains("\"error\":")) {
                    String errorMsg = response.replaceAll(".*\"error\":\"([^\"]*)\".*", "$1");
                    if (!errorMsg.equals(response)) { // If regex replacement worked
                        System.out.println("Error message: " + errorMsg);
                    }
                }

                // Provide troubleshooting tips
                System.out.println("\nTroubleshooting tips:");
                System.out.println("1. Verify your Platform ID and API Key are correct");
                System.out.println("2. Check if the payment ID is valid for your account");
                System.out.println("3. Ensure the orderId uses only allowed characters: A-Za-z0-9+/=-:.,");
                System.out.println("4. Try with a different currency");
            }

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Calculate HMAC-SHA256 signature for Passimpay API
     */
    private static String calculateSignature(String platformId, String requestBody, String apiKey) throws Exception {
        // Create signature contract

        //String signatureContract = platformId + ":" + requestBody + ":" + apiKey;

        String signatureContract = platformId + ";" + requestBody + ";" + apiKey;

        // Generate HMAC-SHA256 signature
        Mac sha256HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(apiKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256HMAC.init(secretKey);

        byte[] hash = sha256HMAC.doFinal(signatureContract.getBytes(StandardCharsets.UTF_8));

        // Convert to hex string
        StringBuilder signature = new StringBuilder();
        for (byte b : hash) {
            signature.append(String.format("%02x", b));
        }

        return signature.toString();
    }

    /**
     * Send HTTP request to Passimpay API
     */
    private static String sendRequest(String url, String requestBody, String signature) throws Exception {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(url);

            // Set headers
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("x-signature", signature);

            // Set request body
            StringEntity entity = new StringEntity(requestBody);
            httpPost.setEntity(entity);

            // Execute request
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                HttpEntity responseEntity = response.getEntity();
                if (responseEntity != null) {
                    return EntityUtils.toString(responseEntity);
                } else {
                    return "No response body";
                }
            }
        }
    }
}