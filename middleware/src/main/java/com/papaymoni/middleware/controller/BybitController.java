package com.papaymoni.middleware.controller;

import com.papaymoni.middleware.dto.ApiResponse;
import com.papaymoni.middleware.dto.BybitApiResponse;
import com.papaymoni.middleware.dto.BybitCredentialsDto;
import com.papaymoni.middleware.model.BybitCredentials;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.repository.UserRepository;
import com.papaymoni.middleware.service.BybitApiService;
import com.papaymoni.middleware.service.BybitCredentialsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/bybit")
public class BybitController {

    private final BybitApiService bybitApiService;
    private final BybitCredentialsService bybitCredentialsService;
    private final UserRepository userRepository;

    public BybitController(BybitApiService bybitApiService,
                           BybitCredentialsService bybitCredentialsService,
                           UserRepository userRepository) {
        this.bybitApiService = bybitApiService;
        this.bybitCredentialsService = bybitCredentialsService;
        this.userRepository = userRepository;
    }

    @PostMapping("/credentials")
    public ResponseEntity<?> saveCredentials(
            @AuthenticationPrincipal UserDetails currentUser,
            @Valid @RequestBody BybitCredentialsDto credentialsDto) {

        try {
            log.info("Saving Bybit credentials for user: {}", currentUser.getUsername());

            // Get the actual User entity from the repository
            User user = userRepository.findByUsername(currentUser.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            BybitCredentials credentials = bybitCredentialsService.saveCredentials(user, credentialsDto);

            // Use your existing ApiResponse format
            ApiResponse response = new ApiResponse();
            response.setSuccess(true);
            response.setMessage("Bybit credentials saved successfully");
            response.setData(credentials);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error saving Bybit credentials", e);

            // Use your existing ApiResponse format
            ApiResponse response = new ApiResponse();
            response.setSuccess(false);
            response.setMessage("An unexpected error occurred: " + e.getMessage());
            response.setData(null);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/credentials")
    public ResponseEntity<?> getCredentials(@AuthenticationPrincipal UserDetails currentUser) {
        try {
            log.info("Getting Bybit credentials for user: {}", currentUser.getUsername());

            // Get the actual User entity from the repository
            User user = userRepository.findByUsername(currentUser.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            BybitCredentials credentials = bybitCredentialsService.getCredentialsByUser(user);

            // Don't expose the API secret in the response
            credentials.setApiSecret("[REDACTED]");

            // Use your existing ApiResponse format
            ApiResponse response = new ApiResponse();
            response.setSuccess(true);
            response.setMessage("Bybit credentials retrieved successfully");
            response.setData(credentials);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error retrieving Bybit credentials", e);

            // Use your existing ApiResponse format
            ApiResponse response = new ApiResponse();
            response.setSuccess(false);
            response.setMessage("An unexpected error occurred: " + e.getMessage());
            response.setData(null);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @DeleteMapping("/credentials")
    public ResponseEntity<?> deleteCredentials(@AuthenticationPrincipal UserDetails currentUser) {
        try {
            log.info("Deleting Bybit credentials for user: {}", currentUser.getUsername());

            // Get the actual User entity from the repository
            User user = userRepository.findByUsername(currentUser.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            BybitCredentials credentials = bybitCredentialsService.getCredentialsByUser(user);
            bybitCredentialsService.deleteCredentials(credentials.getId());

            // Use your existing ApiResponse format
            ApiResponse response = new ApiResponse();
            response.setSuccess(true);
            response.setMessage("Bybit credentials deleted successfully");
            response.setData(null);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error deleting Bybit credentials", e);

            // Use your existing ApiResponse format
            ApiResponse response = new ApiResponse();
            response.setSuccess(false);
            response.setMessage("An unexpected error occurred: " + e.getMessage());
            response.setData(null);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // Support both GET and POST for verification to be flexible
    @RequestMapping(value = "/verify-credentials", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<?> verifyCredentials(@AuthenticationPrincipal UserDetails currentUser) {
        try {
            log.info("Verifying Bybit credentials for user: {}", currentUser.getUsername());

            // Get the actual User entity from the repository
            User user = userRepository.findByUsername(currentUser.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Check if user has credentials before trying to verify
            if (!bybitCredentialsService.hasCredentials(user)) {
                log.warn("User {} does not have Bybit credentials configured", currentUser.getUsername());
                ApiResponse response = new ApiResponse();
                response.setSuccess(false);
                response.setMessage("User does not have Bybit credentials configured");
                response.setData(false);
                return ResponseEntity.ok(response);
            }

            BybitCredentials credentials = bybitCredentialsService.getCredentialsByUser(user);
            boolean isValid = bybitCredentialsService.verifyCredentials(credentials);

            log.info("Bybit credentials verification result for user {}: {}",
                    currentUser.getUsername(), isValid ? "valid" : "invalid");

            // Use your existing ApiResponse format
            ApiResponse response = new ApiResponse();
            response.setSuccess(true);
            response.setMessage(isValid ? "Bybit credentials are valid" : "Bybit credentials are invalid");
            response.setData(isValid);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error verifying Bybit credentials", e);

            // Use your existing ApiResponse format
            ApiResponse response = new ApiResponse();
            response.setSuccess(false);
            response.setMessage("An unexpected error occurred: " + e.getMessage());
            response.setData(null);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/test-connection")
    public ResponseEntity<?> testConnection() {
        log.info("Testing connection to Bybit API");
        boolean connected = bybitApiService.testConnection();

        ApiResponse response = new ApiResponse();
        response.setSuccess(connected);
        response.setMessage(connected ? "Successfully connected to Bybit API" : "Failed to connect to Bybit API");
        response.setData(null);

        return ResponseEntity.ok(response);
    }

    // 1. Get Ads endpoint
    @PostMapping("/ads")
    public ResponseEntity<?> getAds(
            @AuthenticationPrincipal UserDetails currentUser,
            @RequestBody Map<String, String> adRequest) {
        try {
            log.info("Getting ads from Bybit for user: {}", currentUser.getUsername());

            User user = userRepository.findByUsername(currentUser.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (!bybitCredentialsService.hasCredentials(user)) {
                ApiResponse response = new ApiResponse();
                response.setSuccess(false);
                response.setMessage("User does not have Bybit credentials configured");
                response.setData(null);
                return ResponseEntity.badRequest().body(response);
            }

            BybitCredentials credentials = bybitCredentialsService.getCredentialsByUser(user);

            String tokenId = adRequest.getOrDefault("tokenId", "USDT");
            String currencyId = adRequest.getOrDefault("currencyId", "EUR");
            String side = adRequest.getOrDefault("side", "0");

            BybitApiResponse<?> bybitResponse = bybitApiService.getAds(tokenId, currencyId, side, credentials);

            ApiResponse response = new ApiResponse();
            response.setSuccess(bybitResponse.isSuccess());
            response.setMessage(bybitResponse.isSuccess() ? "Ads retrieved successfully" : bybitResponse.getRetMsg());
            response.setData(bybitResponse.getResult());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting ads", e);

            ApiResponse response = new ApiResponse();
            response.setSuccess(false);
            response.setMessage("An unexpected error occurred: " + e.getMessage());
            response.setData(null);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // 2. Create Ad endpoint
    @PostMapping("/ads/create")
    public ResponseEntity<?> createAd(
            @AuthenticationPrincipal UserDetails currentUser,
            @RequestBody Map<String, Object> adPayload) {
        try {
            log.info("Creating Bybit ad for user: {}", currentUser.getUsername());

            User user = userRepository.findByUsername(currentUser.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (!bybitCredentialsService.hasCredentials(user)) {
                ApiResponse response = new ApiResponse();
                response.setSuccess(false);
                response.setMessage("User does not have Bybit credentials configured");
                response.setData(null);
                return ResponseEntity.badRequest().body(response);
            }

            BybitCredentials credentials = bybitCredentialsService.getCredentialsByUser(user);
            BybitApiResponse<?> bybitResponse = bybitApiService.createAd(adPayload, credentials);

            ApiResponse response = new ApiResponse();
            response.setSuccess(bybitResponse.isSuccess());
            response.setMessage(bybitResponse.isSuccess() ? "Ad created successfully" : bybitResponse.getRetMsg());
            response.setData(bybitResponse.getResult());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error creating ad", e);

            ApiResponse response = new ApiResponse();
            response.setSuccess(false);
            response.setMessage("An unexpected error occurred: " + e.getMessage());
            response.setData(null);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // 3. Cancel Ad endpoint
    @PostMapping("/ads/cancel")
    public ResponseEntity<?> cancelAd(
            @AuthenticationPrincipal UserDetails currentUser,
            @RequestBody Map<String, String> cancelRequest) {
        try {
            log.info("Canceling Bybit ad for user: {}", currentUser.getUsername());

            String itemId = cancelRequest.get("itemId");
            if (itemId == null || itemId.isEmpty()) {
                ApiResponse response = new ApiResponse();
                response.setSuccess(false);
                response.setMessage("Item ID is required");
                response.setData(null);
                return ResponseEntity.badRequest().body(response);
            }

            User user = userRepository.findByUsername(currentUser.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (!bybitCredentialsService.hasCredentials(user)) {
                ApiResponse response = new ApiResponse();
                response.setSuccess(false);
                response.setMessage("User does not have Bybit credentials configured");
                response.setData(null);
                return ResponseEntity.badRequest().body(response);
            }

            BybitCredentials credentials = bybitCredentialsService.getCredentialsByUser(user);
            BybitApiResponse<?> bybitResponse = bybitApiService.cancelAd(itemId, credentials);

            ApiResponse response = new ApiResponse();
            response.setSuccess(bybitResponse.isSuccess());
            response.setMessage(bybitResponse.isSuccess() ? "Ad canceled successfully" : bybitResponse.getRetMsg());
            response.setData(bybitResponse.getResult());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error canceling ad", e);

            ApiResponse response = new ApiResponse();
            response.setSuccess(false);
            response.setMessage("An unexpected error occurred: " + e.getMessage());
            response.setData(null);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // 4. Update Ad endpoint
    @PostMapping("/ads/update")
    public ResponseEntity<?> updateAd(
            @AuthenticationPrincipal UserDetails currentUser,
            @RequestBody Map<String, Object> updatePayload) {
        try {
            log.info("Updating Bybit ad for user: {}", currentUser.getUsername());

            User user = userRepository.findByUsername(currentUser.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (!bybitCredentialsService.hasCredentials(user)) {
                ApiResponse response = new ApiResponse();
                response.setSuccess(false);
                response.setMessage("User does not have Bybit credentials configured");
                response.setData(null);
                return ResponseEntity.badRequest().body(response);
            }

            BybitCredentials credentials = bybitCredentialsService.getCredentialsByUser(user);
            BybitApiResponse<?> bybitResponse = bybitApiService.updateAd(updatePayload, credentials);

            ApiResponse response = new ApiResponse();
            response.setSuccess(bybitResponse.isSuccess());
            response.setMessage(bybitResponse.isSuccess() ? "Ad updated successfully" : bybitResponse.getRetMsg());
            response.setData(bybitResponse.getResult());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error updating ad", e);

            ApiResponse response = new ApiResponse();
            response.setSuccess(false);
            response.setMessage("An unexpected error occurred: " + e.getMessage());
            response.setData(null);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // 5. Get Personal Ads endpoint
    @GetMapping("/ads/personal")
    public ResponseEntity<?> getPersonalAds(@AuthenticationPrincipal UserDetails currentUser) {
        try {
            log.info("Getting personal ads for user: {}", currentUser.getUsername());

            User user = userRepository.findByUsername(currentUser.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (!bybitCredentialsService.hasCredentials(user)) {
                ApiResponse response = new ApiResponse();
                response.setSuccess(false);
                response.setMessage("User does not have Bybit credentials configured");
                response.setData(null);
                return ResponseEntity.badRequest().body(response);
            }

            BybitCredentials credentials = bybitCredentialsService.getCredentialsByUser(user);
            BybitApiResponse<?> bybitResponse = bybitApiService.getPersonalAds(credentials);

            ApiResponse response = new ApiResponse();
            response.setSuccess(bybitResponse.isSuccess());
            response.setMessage(bybitResponse.isSuccess() ? "Personal ads retrieved successfully" : bybitResponse.getRetMsg());
            response.setData(bybitResponse.getResult());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting personal ads", e);

            ApiResponse response = new ApiResponse();
            response.setSuccess(false);
            response.setMessage("An unexpected error occurred: " + e.getMessage());
            response.setData(null);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // 6. Get Orders endpoint
    @PostMapping("/orders")
    public ResponseEntity<?> getOrders(
            @AuthenticationPrincipal UserDetails currentUser,
            @RequestBody(required = false) Map<String, Object> filterPayload) {
        try {
            log.info("Getting orders for user: {}", currentUser.getUsername());

            User user = userRepository.findByUsername(currentUser.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (!bybitCredentialsService.hasCredentials(user)) {
                ApiResponse response = new ApiResponse();
                response.setSuccess(false);
                response.setMessage("User does not have Bybit credentials configured");
                response.setData(null);
                return ResponseEntity.badRequest().body(response);
            }

            BybitCredentials credentials = bybitCredentialsService.getCredentialsByUser(user);

            // If no filter payload provided, create an empty one
            if (filterPayload == null) {
                filterPayload = new HashMap<>();
            }

            BybitApiResponse<?> bybitResponse = bybitApiService.getOrders(filterPayload, credentials);

            ApiResponse response = new ApiResponse();
            response.setSuccess(bybitResponse.isSuccess());
            response.setMessage(bybitResponse.isSuccess() ? "Orders retrieved successfully" : bybitResponse.getRetMsg());
            response.setData(bybitResponse.getResult());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting orders", e);

            ApiResponse response = new ApiResponse();
            response.setSuccess(false);
            response.setMessage("An unexpected error occurred: " + e.getMessage());
            response.setData(null);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // 7. Get Order Detail endpoint
    @GetMapping("/orders/{orderId}")
    public ResponseEntity<?> getOrderDetail(
            @AuthenticationPrincipal UserDetails currentUser,
            @PathVariable String orderId) {
        try {
            log.info("Getting order detail for orderId: {}, user: {}", orderId, currentUser.getUsername());

            User user = userRepository.findByUsername(currentUser.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (!bybitCredentialsService.hasCredentials(user)) {
                ApiResponse response = new ApiResponse();
                response.setSuccess(false);
                response.setMessage("User does not have Bybit credentials configured");
                response.setData(null);
                return ResponseEntity.badRequest().body(response);
            }

            BybitCredentials credentials = bybitCredentialsService.getCredentialsByUser(user);
            BybitApiResponse<?> bybitResponse = bybitApiService.getOrderDetail(orderId, credentials);

            ApiResponse response = new ApiResponse();
            response.setSuccess(bybitResponse.isSuccess());
            response.setMessage(bybitResponse.isSuccess() ? "Order detail retrieved successfully" : bybitResponse.getRetMsg());
            response.setData(bybitResponse.getResult());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting order detail", e);

            ApiResponse response = new ApiResponse();
            response.setSuccess(false);
            response.setMessage("An unexpected error occurred: " + e.getMessage());
            response.setData(null);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // 8. Get Pending Orders endpoint
    @PostMapping("/orders/pending")
    public ResponseEntity<?> getPendingOrders(
            @AuthenticationPrincipal UserDetails currentUser,
            @RequestBody(required = false) Map<String, Object> filterPayload) {
        try {
            log.info("Getting pending orders for user: {}", currentUser.getUsername());

            User user = userRepository.findByUsername(currentUser.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (!bybitCredentialsService.hasCredentials(user)) {
                ApiResponse response = new ApiResponse();
                response.setSuccess(false);
                response.setMessage("User does not have Bybit credentials configured");
                response.setData(null);
                return ResponseEntity.badRequest().body(response);
            }

            BybitCredentials credentials = bybitCredentialsService.getCredentialsByUser(user);

            // If no filter payload provided, create an empty one
            if (filterPayload == null) {
                filterPayload = new HashMap<>();
            }

            BybitApiResponse<?> bybitResponse = bybitApiService.getPendingOrders(filterPayload, credentials);

            ApiResponse response = new ApiResponse();
            response.setSuccess(bybitResponse.isSuccess());
            response.setMessage(bybitResponse.isSuccess() ? "Pending orders retrieved successfully" : bybitResponse.getRetMsg());
            response.setData(bybitResponse.getResult());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting pending orders", e);

            ApiResponse response = new ApiResponse();
            response.setSuccess(false);
            response.setMessage("An unexpected error occurred: " + e.getMessage());
            response.setData(null);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // 9. Mark Order as Paid endpoint
    @PostMapping("/orders/{orderId}/pay")
    public ResponseEntity<?> markOrderAsPaid(
            @AuthenticationPrincipal UserDetails currentUser,
            @PathVariable String orderId,
            @RequestBody Map<String, String> paymentInfo) {
        try {
            log.info("Marking order as paid for orderId: {}, user: {}", orderId, currentUser.getUsername());

            String paymentType = paymentInfo.get("paymentType");
            String paymentId = paymentInfo.get("paymentId");

            if (paymentType == null || paymentId == null) {
                ApiResponse response = new ApiResponse();
                response.setSuccess(false);
                response.setMessage("Payment type and payment ID are required");
                response.setData(null);
                return ResponseEntity.badRequest().body(response);
            }

            User user = userRepository.findByUsername(currentUser.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (!bybitCredentialsService.hasCredentials(user)) {
                ApiResponse response = new ApiResponse();
                response.setSuccess(false);
                response.setMessage("User does not have Bybit credentials configured");
                response.setData(null);
                return ResponseEntity.badRequest().body(response);
            }

            BybitCredentials credentials = bybitCredentialsService.getCredentialsByUser(user);
            BybitApiResponse<?> bybitResponse = bybitApiService.markOrderAsPaid(orderId, paymentType, paymentId, credentials);

            ApiResponse response = new ApiResponse();
            response.setSuccess(bybitResponse.isSuccess());
            response.setMessage(bybitResponse.isSuccess() ? "Order marked as paid successfully" : bybitResponse.getRetMsg());
            response.setData(bybitResponse.getResult());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error marking order as paid", e);

            ApiResponse response = new ApiResponse();
            response.setSuccess(false);
            response.setMessage("An unexpected error occurred: " + e.getMessage());
            response.setData(null);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // 10. Release Assets endpoint
    @PostMapping("/orders/{orderId}/release")
    public ResponseEntity<?> releaseAssets(
            @AuthenticationPrincipal UserDetails currentUser,
            @PathVariable String orderId) {
        try {
            log.info("Releasing assets for orderId: {}, user: {}", orderId, currentUser.getUsername());

            User user = userRepository.findByUsername(currentUser.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (!bybitCredentialsService.hasCredentials(user)) {
                ApiResponse response = new ApiResponse();
                response.setSuccess(false);
                response.setMessage("User does not have Bybit credentials configured");
                response.setData(null);
                return ResponseEntity.badRequest().body(response);
            }

            BybitCredentials credentials = bybitCredentialsService.getCredentialsByUser(user);
            BybitApiResponse<?> bybitResponse = bybitApiService.releaseAssets(orderId, credentials);

            ApiResponse response = new ApiResponse();
            response.setSuccess(bybitResponse.isSuccess());
            response.setMessage(bybitResponse.isSuccess() ? "Assets released successfully" : bybitResponse.getRetMsg());
            response.setData(bybitResponse.getResult());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error releasing assets", e);

            ApiResponse response = new ApiResponse();
            response.setSuccess(false);
            response.setMessage("An unexpected error occurred: " + e.getMessage());
            response.setData(null);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // 11. Send Chat Message endpoint
    @PostMapping("/orders/{orderId}/chat")
    public ResponseEntity<?> sendChatMessage(
            @AuthenticationPrincipal UserDetails currentUser,
            @PathVariable String orderId,
            @RequestBody Map<String, String> chatMessage) {
        try {
            log.info("Sending chat message for orderId: {}, user: {}", orderId, currentUser.getUsername());

            String message = chatMessage.get("message");
            String contentType = chatMessage.getOrDefault("contentType", "str");

            if (message == null || message.isEmpty()) {
                ApiResponse response = new ApiResponse();
                response.setSuccess(false);
                response.setMessage("Message is required");
                response.setData(null);
                return ResponseEntity.badRequest().body(response);
            }

            User user = userRepository.findByUsername(currentUser.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (!bybitCredentialsService.hasCredentials(user)) {
                ApiResponse response = new ApiResponse();
                response.setSuccess(false);
                response.setMessage("User does not have Bybit credentials configured");
                response.setData(null);
                return ResponseEntity.badRequest().body(response);
            }

            BybitCredentials credentials = bybitCredentialsService.getCredentialsByUser(user);
            BybitApiResponse<?> bybitResponse = bybitApiService.sendChatMessage(message, contentType, orderId, credentials);

            ApiResponse response = new ApiResponse();
            response.setSuccess(bybitResponse.isSuccess());
            response.setMessage(bybitResponse.isSuccess() ? "Chat message sent successfully" : bybitResponse.getRetMsg());
            response.setData(bybitResponse.getResult());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error sending chat message", e);

            ApiResponse response = new ApiResponse();
            response.setSuccess(false);
            response.setMessage("An unexpected error occurred: " + e.getMessage());
            response.setData(null);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // 12. Upload Chat File endpoint
    @PostMapping("/orders/{orderId}/upload")
    public ResponseEntity<?> uploadChatFile(
            @AuthenticationPrincipal UserDetails currentUser,
            @PathVariable String orderId,
            @RequestParam("file") MultipartFile file) {
        try {
            log.info("Uploading chat file for orderId: {}, user: {}", orderId, currentUser.getUsername());

            if (file.isEmpty()) {
                ApiResponse response = new ApiResponse();
                response.setSuccess(false);
                response.setMessage("File is required");
                response.setData(null);
                return ResponseEntity.badRequest().body(response);
            }

            User user = userRepository.findByUsername(currentUser.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (!bybitCredentialsService.hasCredentials(user)) {
                ApiResponse response = new ApiResponse();
                response.setSuccess(false);
                response.setMessage("User does not have Bybit credentials configured");
                response.setData(null);
                return ResponseEntity.badRequest().body(response);
            }

            BybitCredentials credentials = bybitCredentialsService.getCredentialsByUser(user);
            BybitApiResponse<?> bybitResponse = bybitApiService.uploadChatFile(
                    file.getBytes(), file.getOriginalFilename(), orderId, credentials);

            ApiResponse response = new ApiResponse();
            response.setSuccess(bybitResponse.isSuccess());
            response.setMessage(bybitResponse.isSuccess() ? "File uploaded successfully" : bybitResponse.getRetMsg());
            response.setData(bybitResponse.getResult());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error uploading chat file", e);

            ApiResponse response = new ApiResponse();
            response.setSuccess(false);
            response.setMessage("An unexpected error occurred: " + e.getMessage());
            response.setData(null);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // Additional helper endpoint to get payment methods
    @GetMapping("/payment-methods")
    public ResponseEntity<?> getPaymentMethods(@AuthenticationPrincipal UserDetails currentUser) {
        try {
            log.info("Getting available payment methods");

            // Here you would typically call the Bybit API to get available payment methods
            // For now, we'll return a mock list of common payment methods
            Map<String, Object> paymentMethods = new HashMap<>();

            Map<String, String> bankTransfer = new HashMap<>();
            bankTransfer.put("id", "14");
            bankTransfer.put("name", "Bank Transfer");

            Map<String, String> paypal = new HashMap<>();
            paypal.put("id", "377");
            paypal.put("name", "Balance");

            paymentMethods.put("methods", new Object[]{bankTransfer, paypal});

            ApiResponse response = new ApiResponse();
            response.setSuccess(true);
            response.setMessage("Payment methods retrieved successfully");
            response.setData(paymentMethods);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting payment methods", e);

            ApiResponse response = new ApiResponse();
            response.setSuccess(false);
            response.setMessage("An unexpected error occurred: " + e.getMessage());
            response.setData(null);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // Dashboard statistics endpoint
    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboardStats(@AuthenticationPrincipal UserDetails currentUser) {
        try {
            log.info("Getting dashboard statistics for user: {}", currentUser.getUsername());

            User user = userRepository.findByUsername(currentUser.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (!bybitCredentialsService.hasCredentials(user)) {
                ApiResponse response = new ApiResponse();
                response.setSuccess(false);
                response.setMessage("User does not have Bybit credentials configured");
                response.setData(null);
                return ResponseEntity.badRequest().body(response);
            }

            // Here you would call various Bybit API endpoints to gather statistics
            // For now, we'll create a mock dashboard response
            Map<String, Object> dashboardStats = new HashMap<>();

            // Get a count of user's active ads
            BybitCredentials credentials = bybitCredentialsService.getCredentialsByUser(user);
            BybitApiResponse<?> personalAdsResponse = bybitApiService.getPersonalAds(credentials);

            // Get a count of pending orders
            Map<String, Object> pendingOrdersPayload = new HashMap<>();
            pendingOrdersPayload.put("page", 1);
            pendingOrdersPayload.put("size", 1);
            BybitApiResponse<?> pendingOrdersResponse = bybitApiService.getPendingOrders(pendingOrdersPayload, credentials);

            // Extract counts from responses
            int activeAdsCount = 0;
            int pendingOrdersCount = 0;
            int completedOrdersCount = 0;

            if (personalAdsResponse != null && personalAdsResponse.isSuccess()) {
                Map<String, Object> result = (Map<String, Object>) personalAdsResponse.getResult();
                if (result != null && result.containsKey("count")) {
                    activeAdsCount = ((Number) result.get("count")).intValue();
                }
            }

            if (pendingOrdersResponse != null && pendingOrdersResponse.isSuccess()) {
                Map<String, Object> result = (Map<String, Object>) pendingOrdersResponse.getResult();
                if (result != null && result.containsKey("count")) {
                    pendingOrdersCount = ((Number) result.get("count")).intValue();
                }
            }

            // Add statistics to the dashboard response
            dashboardStats.put("activeAdsCount", activeAdsCount);
            dashboardStats.put("pendingOrdersCount", pendingOrdersCount);
            dashboardStats.put("completedOrdersCount", completedOrdersCount);

            ApiResponse response = new ApiResponse();
            response.setSuccess(true);
            response.setMessage("Dashboard statistics retrieved successfully");
            response.setData(dashboardStats);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting dashboard statistics", e);

            ApiResponse response = new ApiResponse();
            response.setSuccess(false);
            response.setMessage("An unexpected error occurred: " + e.getMessage());
            response.setData(null);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}