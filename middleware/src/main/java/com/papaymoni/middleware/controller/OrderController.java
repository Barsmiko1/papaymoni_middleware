package com.papaymoni.middleware.controller;

import com.papaymoni.middleware.dto.ApiResponse;
import com.papaymoni.middleware.dto.OrderDto;
import com.papaymoni.middleware.model.Order;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.security.CurrentUser;
import com.papaymoni.middleware.service.OrderService;
import com.papaymoni.middleware.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
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

        User user = userService.getUserByUsername(currentUser.getUsername());
        Order order = orderService.createOrder(user, orderDto);

        return new ResponseEntity<>(ApiResponse.success("Order created successfully", order), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Order>>> getUserOrders(
            @AuthenticationPrincipal UserDetails currentUser) {

        User user = userService.getUserByUsername(currentUser.getUsername());
        List<Order> orders = orderService.getUserOrders(user);

        return ResponseEntity.ok(ApiResponse.success(orders));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<Order>> getOrderById(
            @AuthenticationPrincipal UserDetails currentUser,
            @PathVariable String orderId) {

        Order order = orderService.getOrderById(orderId);

        // Check if the order belongs to the current user
        User user = userService.getUserByUsername(currentUser.getUsername());
        if (!order.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("You don't have permission to access this order"));
        }

        return ResponseEntity.ok(ApiResponse.success(order));
    }

    @PostMapping("/{orderId}/pay")
    public ResponseEntity<ApiResponse<Void>> markOrderAsPaid(
            @AuthenticationPrincipal UserDetails currentUser,
            @PathVariable String orderId,
            @RequestBody Map<String, String> paymentDetails) {

        Order order = orderService.getOrderById(orderId);

        // Check if the order belongs to the current user
        User user = userService.getUserByUsername(currentUser.getUsername());
        if (!order.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("You don't have permission to access this order"));
        }

        orderService.markOrderAsPaid(orderId, paymentDetails.get("paymentType"), paymentDetails.get("paymentId"));

        return ResponseEntity.ok(ApiResponse.success("Order marked as paid successfully", null));
    }

    @PostMapping("/{orderId}/release")
    public ResponseEntity<ApiResponse<Void>> releaseAssets(
            @AuthenticationPrincipal UserDetails currentUser,
            @PathVariable String orderId) {

        Order order = orderService.getOrderById(orderId);

        // Check if the order belongs to the current user
        User user = userService.getUserByUsername(currentUser.getUsername());
        if (!order.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("You don't have permission to access this order"));
        }

        orderService.releaseAssets(orderId);

        return ResponseEntity.ok(ApiResponse.success("Assets released successfully", null));
    }

    @PostMapping("/{orderId}/message")
    public ResponseEntity<ApiResponse<Void>> sendOrderMessage(
            @AuthenticationPrincipal UserDetails currentUser,
            @PathVariable String orderId,
            @RequestBody Map<String, String> messageDetails) {

        Order order = orderService.getOrderById(orderId);

        // Check if the order belongs to the current user
        User user = userService.getUserByUsername(currentUser.getUsername());
        if (!order.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("You don't have permission to access this order"));
        }

        orderService.sendOrderMessage(orderId, messageDetails.get("message"));

        return ResponseEntity.ok(ApiResponse.success("Message sent successfully", null));
    }

    @PostMapping("/{orderId}/receipt")
    public ResponseEntity<ApiResponse<Void>> uploadOrderReceipt(
            @AuthenticationPrincipal UserDetails currentUser,
            @PathVariable String orderId,
            @RequestParam("file") MultipartFile file) throws IOException {

        Order order = orderService.getOrderById(orderId);

        // Check if the order belongs to the current user
        User user = userService.getUserByUsername(currentUser.getUsername());
        if (!order.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("You don't have permission to access this order"));
        }

        orderService.uploadOrderReceipt(orderId, file.getBytes(), file.getOriginalFilename());

        return ResponseEntity.ok(ApiResponse.success("Receipt uploaded successfully", null));
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelOrder(
            @AuthenticationPrincipal UserDetails currentUser,
            @PathVariable String orderId) {

        Order order = orderService.getOrderById(orderId);

        // Check if the order belongs to the current user
        User user = userService.getUserByUsername(currentUser.getUsername());
        if (!order.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("You don't have permission to access this order"));
        }

        orderService.cancelOrder(orderId);

        return ResponseEntity.ok(ApiResponse.success("Order cancelled successfully", null));
    }
}
