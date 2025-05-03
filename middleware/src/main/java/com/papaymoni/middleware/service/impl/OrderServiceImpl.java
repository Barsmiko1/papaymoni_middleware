package com.papaymoni.middleware.service.impl;

import com.papaymoni.middleware.dto.BybitApiResponse;
import com.papaymoni.middleware.dto.OrderDto;
import com.papaymoni.middleware.event.OrderCreatedEvent;
import com.papaymoni.middleware.event.OrderPaidEvent;
import com.papaymoni.middleware.exception.AssetReleaseException;
import com.papaymoni.middleware.exception.InsufficientBalanceException;
import com.papaymoni.middleware.exception.PaymentProcessingException;
import com.papaymoni.middleware.exception.ResourceNotFoundException;
import com.papaymoni.middleware.model.BybitCredentials;
import com.papaymoni.middleware.model.Order;
import com.papaymoni.middleware.model.Transaction;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.model.enums.OrderStatus;
import com.papaymoni.middleware.repository.OrderRepository;
import com.papaymoni.middleware.service.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static com.papaymoni.middleware.config.RabbitMQConfig.*;

@Service
public class OrderServiceImpl implements OrderService {
    private final OrderRepository orderRepository;
    private final BybitApiService bybitApiService;
    private final BybitCredentialsService bybitCredentialsService;
    private final TransactionService transactionService;
    private final PaymentService paymentService;
    private final RabbitTemplate rabbitTemplate;

    public OrderServiceImpl(OrderRepository orderRepository,
                            BybitApiService bybitApiService,
                            BybitCredentialsService bybitCredentialsService,
                            TransactionService transactionService,
                            PaymentService paymentService,
                            RabbitTemplate rabbitTemplate) {
        this.orderRepository = orderRepository;
        this.bybitApiService = bybitApiService;
        this.bybitCredentialsService = bybitCredentialsService;
        this.transactionService = transactionService;
        this.paymentService = paymentService;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    @Transactional
    public Order createOrder(User user, OrderDto orderDto) {
        // Create new order
        Order order = new Order();
        order.setId(orderDto.getId());
        order.setUser(user);
        order.setBybitItemId(orderDto.getBybitItemId());
        order.setTokenId(orderDto.getTokenId());
        order.setCurrencyId(orderDto.getCurrencyId());
        order.setSide(orderDto.getSide());
        order.setOrderType(orderDto.getOrderType());
        order.setAmount(orderDto.getAmount());
        order.setPrice(orderDto.getPrice());
        order.setQuantity(orderDto.getQuantity());
        order.setTargetUserId(orderDto.getTargetUserId());
        order.setTargetNickName(orderDto.getTargetNickName());
        order.setStatus(OrderStatus.CREATED.getCode());

        Order savedOrder = orderRepository.save(order);

        // Publish order created event
        rabbitTemplate.convertAndSend(ORDER_EXCHANGE, ORDER_CREATED_KEY,
                new OrderCreatedEvent(savedOrder.getId(), user.getId()));

        return savedOrder;
    }

    @Override
    public Order getOrderById(String orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
    }

    @Override
    public List<Order> getUserOrders(User user) {
        return orderRepository.findByUser(user);
    }

    @Override
    public List<Order> getPendingOrders(User user) {
        return orderRepository.findByUserAndStatusIn(user,
                Arrays.asList(OrderStatus.WAITING_FOR_PAYMENT.getCode(), OrderStatus.PAID.getCode()));
    }

    @Override
    public List<Order> getPendingBuyOrders() {
        return orderRepository.findBySideAndStatus(0, OrderStatus.WAITING_FOR_PAYMENT.getCode());
    }

    @Override
    public List<Order> getPendingSellOrders() {
        return orderRepository.findBySideAndStatus(1, OrderStatus.PAID.getCode());
    }

    @Override
    @Transactional
    public void processOrder(Order order) {
        if (order.getSide() == 0) { // Buy order
            processBuyOrder(order);
        } else { // Sell order
            processSellOrder(order);
        }
    }

    private void processBuyOrder(Order order) {
        User user = order.getUser();

        // Check if user has sufficient balance
        if (!paymentService.hasSufficientBalance(user, order.getAmount())) {
            throw new InsufficientBalanceException("Insufficient balance to process order");
        }

        // Process payment
        Transaction transaction = paymentService.processPayment(user, order);

        // Get user's Bybit credentials
        BybitCredentials credentials = bybitCredentialsService.getCredentialsByUser(user);

        // Mark order as paid on Bybit
        BybitApiResponse<?> response = bybitApiService.markOrderAsPaid(
                order.getId(),
                transaction.getPaymentMethod(),
                transaction.getPaymentDetails(),
                credentials
        );

        if (response.isSuccess()) {
            order.setStatus(OrderStatus.PAID.getCode());
            orderRepository.save(order);

            // Publish order paid event
            rabbitTemplate.convertAndSend(ORDER_EXCHANGE, ORDER_UPDATED_KEY,
                    new OrderPaidEvent(order.getId(), user.getId()));

            // Send confirmation message
            sendOrderMessage(order.getId(), "Hi, payment has been made using Bybit OpenAPI. Kindly check your bank account and release the asset.");

            // Upload receipt if available
            if (transaction.getReceiptUrl() != null) {
                byte[] receiptData = paymentService.getReceiptData(transaction);
                uploadOrderReceipt(order.getId(), receiptData, "payment_receipt.png");
            }
        } else {
            throw new PaymentProcessingException("Failed to mark order as paid on Bybit: " + response.getRetMsg());
        }
    }

    private void processSellOrder(Order order) {
        User user = order.getUser();

        // Verify deposit in the system
        Transaction deposit = transactionService.findMatchingDeposit(
                user,
                order.getAmount(),
                order.getTargetNickName()
        );

        if (deposit != null) {
            // Get user's Bybit credentials
            BybitCredentials credentials = bybitCredentialsService.getCredentialsByUser(user);

            // Release assets on Bybit
            BybitApiResponse<?> response = bybitApiService.releaseAssets(
                    order.getId(),
                    credentials
            );

            if (response.isSuccess()) {
                order.setStatus(OrderStatus.COMPLETED.getCode());
                order.setCompletedAt(LocalDateTime.now());
                orderRepository.save(order);

                // Send confirmation message
                sendOrderMessage(order.getId(), "Thank you for trading with us. We use the Bybit OpenAPI powered by Papay Moni.");
            } else {
                throw new AssetReleaseException("Failed to release assets on Bybit: " + response.getRetMsg());
            }
        }
    }

    @Override
    @Transactional
    public void markOrderAsPaid(String orderId, String paymentType, String paymentId) {
        Order order = getOrderById(orderId);
        User user = order.getUser();

        // Get user's Bybit credentials
        BybitCredentials credentials = bybitCredentialsService.getCredentialsByUser(user);

        BybitApiResponse<?> response = bybitApiService.markOrderAsPaid(
                orderId,
                paymentType,
                paymentId,
                credentials
        );

        if (response.isSuccess()) {
            order.setStatus(OrderStatus.PAID.getCode());
            orderRepository.save(order);

            // Publish order paid event
            rabbitTemplate.convertAndSend(ORDER_EXCHANGE, ORDER_UPDATED_KEY,
                    new OrderPaidEvent(order.getId(), user.getId()));
        } else {
            throw new PaymentProcessingException("Failed to mark order as paid on Bybit: " + response.getRetMsg());
        }
    }

    @Override
    @Transactional
    public void releaseAssets(String orderId) {
        Order order = getOrderById(orderId);
        User user = order.getUser();

        // Get user's Bybit credentials
        BybitCredentials credentials = bybitCredentialsService.getCredentialsByUser(user);

        BybitApiResponse<?> response = bybitApiService.releaseAssets(
                orderId,
                credentials
        );

        if (response.isSuccess()) {
            order.setStatus(OrderStatus.COMPLETED.getCode());
            order.setCompletedAt(LocalDateTime.now());
            orderRepository.save(order);
        } else {
            throw new AssetReleaseException("Failed to release assets on Bybit: " + response.getRetMsg());
        }
    }

    @Override
    public void sendOrderMessage(String orderId, String message) {
        Order order = getOrderById(orderId);
        User user = order.getUser();

        // Get user's Bybit credentials
        BybitCredentials credentials = bybitCredentialsService.getCredentialsByUser(user);

        bybitApiService.sendChatMessage(
                message,
                "str",
                orderId,
                credentials
        );
    }

    @Override
    public void uploadOrderReceipt(String orderId, byte[] receiptData, String filename) {
        Order order = getOrderById(orderId);
        User user = order.getUser();

        // Get user's Bybit credentials
        BybitCredentials credentials = bybitCredentialsService.getCredentialsByUser(user);

        bybitApiService.uploadChatFile(
                receiptData,
                filename,
                orderId,
                credentials
        );
    }

    @Override
    @Transactional
    public void cancelOrder(String orderId) {
        Order order = getOrderById(orderId);

        if (order.getStatus() == OrderStatus.COMPLETED.getCode()) {
            throw new IllegalStateException("Cannot cancel a completed order");
        }

        order.setStatus(OrderStatus.CANCELLED.getCode());
        orderRepository.save(order);
    }

    /**
     * Get total count of orders in the system
     * @return Count of orders
     */
    @Override
    @Transactional(readOnly = true)
    public long getOrderCount() {
        try {
            return orderRepository.count();
        } catch (Exception e) {
            //log.error("Error getting order count", e);
            return -1; // Indicate error
        }
    }
}
