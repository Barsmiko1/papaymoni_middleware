package com.papaymoni.middleware.controller;

import com.papaymoni.middleware.dto.ApiResponse;
import com.papaymoni.middleware.dto.OrderDto;
import com.papaymoni.middleware.exception.AssetReleaseException;
import com.papaymoni.middleware.exception.OrderProcessingException;
import com.papaymoni.middleware.exception.PaymentProcessingException;
import com.papaymoni.middleware.exception.ResourceNotFoundException;
import com.papaymoni.middleware.model.Order;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.service.OrderService;
import com.papaymoni.middleware.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@Slf4j
public class OrderController {

    private final OrderService orderService;
    private final UserService userService;

    public OrderController(OrderService orderService, UserService userService) {
        this.orderService = orderService;
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Order>> createOrder(
            @AuthenticationPrincipal UserDetails currentUser,
            @Valid @RequestBody OrderDto orderDto) {
        try {
            log.info("Creating order for user: {}", currentUser.getUsername());
            User user = userService.getUserByUsername(currentUser.getUsername());
            Order order = orderService.createOrder(user, orderDto);

            return new ResponseEntity<>(
                    ApiResponse.success("Order created successfully", order),
                    HttpStatus.CREATED
            );
        } catch (Exception e) {
            log.error("Error creating order: {}", e.getMessage(), e);
            return handleOrderException(e, "Error creating order");
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Order>>> getUserOrders(
            @AuthenticationPrincipal UserDetails currentUser) {
        try {
            log.info("Fetching orders for user: {}", currentUser.getUsername());
            User user = userService.getUserByUsername(currentUser.getUsername());
            List<Order> orders = orderService.getUserOrders(user);

            return ResponseEntity.ok(
                    ApiResponse.success("Orders retrieved successfully", orders)
            );
        } catch (Exception e) {
            log.error("Error fetching user orders: {}", e.getMessage(), e);
            return handleOrderException(e, "Error fetching orders");
        }
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<Order>> getOrderById(
            @AuthenticationPrincipal UserDetails currentUser,
            @PathVariable String orderId) {
        try {
            log.info("Fetching order: {} for user: {}", orderId, currentUser.getUsername());
            Order order = orderService.getOrderById(orderId);

            // Check if the order belongs to the current user
            User user = userService.getUserByUsername(currentUser.getUsername());
            validateOrderOwnership(order, user);

            return ResponseEntity.ok(
                    ApiResponse.success("Order retrieved successfully", order)
            );
        } catch (Exception e) {
            log.error("Error fetching order {}: {}", orderId, e.getMessage(), e);
            return handleOrderException(e, "Error fetching order");
        }
    }

    @PostMapping("/{orderId}/pay")
    public ResponseEntity<ApiResponse<Void>> markOrderAsPaid(
            @AuthenticationPrincipal UserDetails currentUser,
            @PathVariable String orderId,
            @RequestBody Map<String, String> paymentDetails) {
        try {
            log.info("Marking order as paid: {} for user: {}", orderId, currentUser.getUsername());

            // Validate required fields
            if (paymentDetails == null || !paymentDetails.containsKey("paymentType") || !paymentDetails.containsKey("paymentId")) {
                return ResponseEntity.badRequest().body(
                        ApiResponse.error("Payment type and payment ID are required")
                );
            }

            Order order = orderService.getOrderById(orderId);

            // Check if the order belongs to the current user
            User user = userService.getUserByUsername(currentUser.getUsername());
            validateOrderOwnership(order, user);

            orderService.markOrderAsPaid(orderId, paymentDetails.get("paymentType"), paymentDetails.get("paymentId"));

            return ResponseEntity.ok(
                    ApiResponse.success("Order marked as paid successfully", null)
            );
        } catch (Exception e) {
            log.error("Error marking order {} as paid: {}", orderId, e.getMessage(), e);
            return handleOrderException(e, "Error marking order as paid");
        }
    }

    @PostMapping("/{orderId}/release")
    public ResponseEntity<ApiResponse<Void>> releaseAssets(
            @AuthenticationPrincipal UserDetails currentUser,
            @PathVariable String orderId) {
        try {
            log.info("Releasing assets for order: {} by user: {}", orderId, currentUser.getUsername());

            Order order = orderService.getOrderById(orderId);

            // Check if the order belongs to the current user
            User user = userService.getUserByUsername(currentUser.getUsername());
            validateOrderOwnership(order, user);

            orderService.releaseAssets(orderId);

            return ResponseEntity.ok(
                    ApiResponse.success("Assets released successfully", null)
            );
        } catch (Exception e) {
            log.error("Error releasing assets for order {}: {}", orderId, e.getMessage(), e);
            return handleOrderException(e, "Error releasing assets");
        }
    }

    @PostMapping("/{orderId}/message")
    public ResponseEntity<ApiResponse<Void>> sendOrderMessage(
            @AuthenticationPrincipal UserDetails currentUser,
            @PathVariable String orderId,
            @RequestBody Map<String, String> messageDetails) {
        try {
            log.info("Sending message for order: {} by user: {}", orderId, currentUser.getUsername());

            // Validate required fields
            if (messageDetails == null || !messageDetails.containsKey("message") || messageDetails.get("message").isEmpty()) {
                return ResponseEntity.badRequest().body(
                        ApiResponse.error("Message content is required")
                );
            }

            Order order = orderService.getOrderById(orderId);

            // Check if the order belongs to the current user
            User user = userService.getUserByUsername(currentUser.getUsername());
            validateOrderOwnership(order, user);

            // Sanitize message to prevent injection attacks
            String message = sanitizeMessage(messageDetails.get("message"));

            orderService.sendOrderMessage(orderId, message);

            return ResponseEntity.ok(
                    ApiResponse.success("Message sent successfully", null)
            );
        } catch (Exception e) {
            log.error("Error sending message for order {}: {}", orderId, e.getMessage(), e);
            return handleOrderException(e, "Error sending message");
        }
    }

    @PostMapping("/{orderId}/receipt")
    public ResponseEntity<ApiResponse<Void>> uploadOrderReceipt(
            @AuthenticationPrincipal UserDetails currentUser,
            @PathVariable String orderId,
            @RequestParam("file") MultipartFile file) {
        try {
            log.info("Uploading receipt for order: {} by user: {}", orderId, currentUser.getUsername());

            // Validate file
            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().body(
                        ApiResponse.error("Receipt file is required")
                );
            }

            // Validate file size (max 5MB)
            if (file.getSize() > 5 * 1024 * 1024) {
                return ResponseEntity.badRequest().body(
                        ApiResponse.error("File size exceeds the maximum limit (5MB)")
                );
            }

            // Validate file type (only images and PDFs)
            String contentType = file.getContentType();
            if (contentType == null || !(contentType.startsWith("image/") || contentType.equals("application/pdf"))) {
                return ResponseEntity.badRequest().body(
                        ApiResponse.error("Only image and PDF files are allowed")
                );
            }

            Order order = orderService.getOrderById(orderId);

            // Check if the order belongs to the current user
            User user = userService.getUserByUsername(currentUser.getUsername());
            validateOrderOwnership(order, user);

            orderService.uploadOrderReceipt(orderId, file.getBytes(), file.getOriginalFilename());

            return ResponseEntity.ok(
                    ApiResponse.success("Receipt uploaded successfully", null)
            );
        } catch (IOException e) {
            log.error("Error reading receipt file for order {}: {}", orderId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Error reading receipt file: " + e.getMessage())
            );
        } catch (Exception e) {
            log.error("Error uploading receipt for order {}: {}", orderId, e.getMessage(), e);
            return handleOrderException(e, "Error uploading receipt");
        }
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelOrder(
            @AuthenticationPrincipal UserDetails currentUser,
            @PathVariable String orderId) {
        try {
            log.info("Cancelling order: {} by user: {}", orderId, currentUser.getUsername());

            Order order = orderService.getOrderById(orderId);

            // Check if the order belongs to the current user
            User user = userService.getUserByUsername(currentUser.getUsername());
            validateOrderOwnership(order, user);

            orderService.cancelOrder(orderId);

            return ResponseEntity.ok(
                    ApiResponse.success("Order cancelled successfully", null)
            );
        } catch (Exception e) {
            log.error("Error cancelling order {}: {}", orderId, e.getMessage(), e);
            return handleOrderException(e, "Error cancelling order");
        }
    }

    /**
     * Helper method to validate order ownership
     * @param order The order to check
     * @param user The current user
     * @throws AccessDeniedException If the user doesn't own the order
     */
    private void validateOrderOwnership(Order order, User user) {
        if (!order.getUser().getId().equals(user.getId())) {
            log.warn("User {} attempted to access order {} belonging to user {}",
                    user.getId(), order.getId(), order.getUser().getId());
            throw new AccessDeniedException("You don't have permission to access this order");
        }
    }

    /**
     * Helper method to sanitize message content
     * @param message The message to sanitize
     * @return Sanitized message
     */
    private String sanitizeMessage(String message) {
        if (message == null) {
            return "";
        }

        // Trim message to reasonable length
        if (message.length() > 1000) {
            message = message.substring(0, 1000);
        }

        // Basic XSS protection - remove HTML tags
        message = message.replaceAll("<[^>]*>", "");

        return message;
    }

    /**
     * Helper method for standardized exception handling
     * @param e The exception
     * @param defaultMessage Default error message
     * @return ResponseEntity with appropriate error status and message
     */
    private <T> ResponseEntity<ApiResponse<T>> handleOrderException(Exception e, String defaultMessage) {
        if (e instanceof ResourceNotFoundException) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } else if (e instanceof AccessDeniedException) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("You don't have permission to access this order"));
        } else if (e instanceof PaymentProcessingException) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Payment processing error: " + e.getMessage()));
        } else if (e instanceof AssetReleaseException) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Asset release error: " + e.getMessage()));
        } else if (e instanceof OrderProcessingException) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Order processing error: " + e.getMessage()));
        } else if (e instanceof IllegalStateException) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        } else {
            // Generic error handling
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(defaultMessage + ": " + e.getMessage()));
        }
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> healthCheck() {
        Map<String, Object> healthData = new HashMap<>();

        try {
            // Check if service is operational
            long orderCount = orderService.getOrderCount();

            healthData.put("status", "UP");
            healthData.put("orderCount", orderCount);
            healthData.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(
                    ApiResponse.success("Order service is healthy", healthData)
            );
        } catch (Exception e) {
            log.error("Health check failed: {}", e.getMessage(), e);

            healthData.put("status", "DOWN");
            healthData.put("error", e.getMessage());
            healthData.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("Order service is experiencing issues", healthData));
        }
    }
}