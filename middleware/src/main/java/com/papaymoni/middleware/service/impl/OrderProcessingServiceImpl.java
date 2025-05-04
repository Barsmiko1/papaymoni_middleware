//package com.papaymoni.middleware.service.impl;
//
//import com.papaymoni.middleware.dto.BybitApiResponse;
//import com.papaymoni.middleware.event.OrderCompletedEvent;
//import com.papaymoni.middleware.event.OrderPaidEvent;
//import com.papaymoni.middleware.exception.AssetReleaseException;
//import com.papaymoni.middleware.exception.InsufficientBalanceException;
//import com.papaymoni.middleware.exception.OrderProcessingException;
//import com.papaymoni.middleware.exception.PaymentProcessingException;
//import com.papaymoni.middleware.model.BybitCredentials;
//import com.papaymoni.middleware.model.Order;
//import com.papaymoni.middleware.model.Transaction;
//import com.papaymoni.middleware.model.User;
//import com.papaymoni.middleware.repository.OrderRepository;
//import com.papaymoni.middleware.service.*;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.amqp.rabbit.core.RabbitTemplate;
//import org.springframework.cache.annotation.CacheEvict;
//import org.springframework.cache.annotation.Cacheable;
//import org.springframework.context.ApplicationEventPublisher;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.time.LocalDateTime;
//import java.util.List;
//import java.util.Map;
//import java.util.Optional;
//import java.util.concurrent.CompletableFuture;
//import java.util.concurrent.atomic.AtomicInteger;
//import java.util.function.Function;
//import java.util.stream.Collectors;
//
//import static com.papaymoni.middleware.config.RabbitMQConfig.*;
//
///**
// * Implementation of OrderProcessingService
// * Handles the processing of buy and sell orders
// * Implements Java 8 best practices with functional programming, streams, and CompletableFuture
// */
//@Slf4j
//@Service
//@RequiredArgsConstructor
//public class OrderProcessingServiceImpl implements OrderProcessingService {
//
//    private final OrderRepository orderRepository;
//    private final BybitApiService bybitApiService;
//    private final BybitCredentialsService bybitCredentialsService;
//    private final PaymentService paymentService;
//    private final GLService glService;
//    private final BankService bankService;
//    private final TransactionService transactionService;
//    private final RabbitTemplate rabbitTemplate;
//    private final ApplicationEventPublisher eventPublisher;
//
//    /**
//     * Process pending buy orders (status=10, side=1)
//     * @return number of orders processed
//     */
//    @Override
//    @Cacheable(value = "pendingBuyOrders", key = "'processingStatus'", condition = "#result == 0")
//    public int processPendingBuyOrders() {
//        log.info("Fetching pending buy orders with status=10 and side=1");
//        List<Order> pendingOrders = orderRepository.findByStatusAndSide(10, 1);
//        log.info("Found {} pending buy orders to process", pendingOrders.size());
//
//        AtomicInteger processedCount = new AtomicInteger(0);
//
//        // Process orders in parallel using Java 8 streams
//        pendingOrders.parallelStream()
//                .map(this::processBuyOrder)
//                .filter(Optional::isPresent)
//                .forEach(order -> processedCount.incrementAndGet());
//
//        return processedCount.get();
//    }
//
//    /**
//     * Process pending sell orders (status=20, side=0)
//     * @return number of orders processed
//     */
//    @Override
//    @Cacheable(value = "pendingSellOrders", key = "'processingStatus'", condition = "#result == 0")
//    public int processPendingSellOrders() {
//        log.info("Fetching pending sell orders with status=20 and side=0");
//        List<Order> pendingOrders = orderRepository.findByStatusAndSide(20, 0);
//        log.info("Found {} pending sell orders to process", pendingOrders.size());
//
//        AtomicInteger processedCount = new AtomicInteger(0);
//
//        // Process orders in parallel using Java 8 streams
//        pendingOrders.parallelStream()
//                .map(this::processSellOrder)
//                .filter(Optional::isPresent)
//                .forEach(order -> processedCount.incrementAndGet());
//
//        return processedCount.get();
//    }
//
//    /**
//     * Process a single buy order asynchronously
//     * @param order the order to process
//     * @return CompletableFuture with the processed order
//     */
//    @Override
//    public CompletableFuture<Optional<Order>> processBuyOrderAsync(Order order) {
//        return CompletableFuture.supplyAsync(() -> processBuyOrder(order));
//    }
//
//    /**
//     * Process a single sell order asynchronously
//     * @param order the order to process
//     * @return CompletableFuture with the processed order
//     */
//    @Override
//    public CompletableFuture<Optional<Order>> processSellOrderAsync(Order order) {
//        return CompletableFuture.supplyAsync(() -> processSellOrder(order));
//    }
//
//    /**
//     * Process a single buy order
//     * @param order the order to process
//     * @return Optional with the processed order
//     */
//    @Override
//    @Transactional
//    @CacheEvict(value = "pendingBuyOrders", allEntries = true)
//    public Optional<Order> processBuyOrder(Order order) {
//        log.info("Processing buy order: {}", order.getId());
//        User user = order.getUser();
//
//        try {
//            // Step 1: Get detailed order information from Bybit
//            BybitCredentials credentials = bybitCredentialsService.getCredentialsByUser(user);
//            BybitApiResponse<?> orderDetails = bybitApiService.getOrderDetail(order.getId(), credentials);
//
//            if (!orderDetails.isSuccess()) {
//                log.error("Failed to get order details from Bybit: {}", orderDetails.getRetMsg());
//                return Optional.empty();
//            }
//
//            log.info("Retrieved order details from Bybit for order: {}", order.getId());
//
//            // Step 2: Check if user has sufficient balance
//            if (!glService.hasSufficientBalance(user, order.getAmount())) {
//                log.error("Insufficient balance for user {} to process order {}", user.getId(), order.getId());
//                throw new InsufficientBalanceException("Insufficient balance to process order");
//            }
//
//            log.info("User {} has sufficient balance for order {}", user.getId(), order.getId());
//
//            // Step 3: Process payment
//            Transaction transaction = paymentService.processBuyOrderPayment(user, order);
//            log.info("Created payment transaction {} for order {}", transaction.getId(), order.getId());
//
//            // Step 4: Validate recipient details via name enquiry
//            boolean recipientValid = bankService.validateRecipient(
//                    order.getTargetNickName(),
//                    transaction.getPaymentDetails()
//            );
//
//            if (!recipientValid) {
//                log.warn("Recipient validation failed for order {}, but proceeding anyway", order.getId());
//            }
//
//            // Step 5: Process bank withdrawal
//            String transferReference = bankService.processTransfer(
//                    transaction.getAmount(),
//                    transaction.getPaymentDetails(),
//                    order.getTargetNickName(),
//                    "Payment for Bybit P2P order " + order.getId()
//            );
//
//            transaction.setExternalReference(transferReference);
//            log.info("Processed bank transfer with reference {} for order {}", transferReference, order.getId());
//
//            // Step 6: Mark order as paid on Bybit
//            BybitApiResponse<?> markPaidResponse = bybitApiService.markOrderAsPaid(
//                    order.getId(),
//                    transaction.getPaymentMethod(),
//                    transaction.getExternalReference(),
//                    credentials
//            );
//
//            if (!markPaidResponse.isSuccess()) {
//                log.error("Failed to mark order as paid on Bybit: {}", markPaidResponse.getRetMsg());
//                throw new PaymentProcessingException("Failed to mark order as paid on Bybit: " + markPaidResponse.getRetMsg());
//            }
//
//            log.info("Successfully marked order {} as paid on Bybit", order.getId());
//
//            // Step 7: Update order status locally
//            order.setStatus(20); // Paid status
//            order.setUpdatedAt(LocalDateTime.now());
//            Order savedOrder = orderRepository.save(order);
//            log.info("Updated order {} status to 20 (Paid)", order.getId());
//
//            // Step 8: Send chat message to seller
//            bybitApiService.sendChatMessage(
//                    "Hi, payment has been made using Bybit OpenAPI powered by Papay Moni. Kindly check your bank account and release the asset.",
//                    "str",
//                    order.getId(),
//                    credentials
//            );
//            log.info("Sent chat message to seller for order {}", order.getId());
//
//            // Step 9: Generate and upload receipt if available
//            Optional.ofNullable(transaction.getReceiptUrl())
//                    .ifPresent(receiptUrl -> {
//                        try {
//                            byte[] receiptData = paymentService.getReceiptData(transaction);
//                            bybitApiService.uploadChatFile(
//                                    receiptData,
//                                    "payment_receipt.png",
//                                    order.getId(),
//                                    credentials
//                            );
//                            log.info("Uploaded payment receipt for order {}", order.getId());
//                        } catch (Exception e) {
//                            log.warn("Failed to upload receipt for order {}: {}", order.getId(), e.getMessage());
//                        }
//                    });
//
//            // Step 10: Publish order paid event
//            OrderPaidEvent event = new OrderPaidEvent(order.getId(), user.getId());
//            rabbitTemplate.convertAndSend(ORDER_EXCHANGE, ORDER_UPDATED_KEY, event);
//            eventPublisher.publishEvent(event);
//            log.info("Published OrderPaidEvent for order {}", order.getId());
//
//            return Optional.of(savedOrder);
//
//        } catch (InsufficientBalanceException | PaymentProcessingException e) {
//            log.error("Error processing buy order {}: {}", order.getId(), e.getMessage());
//            throw e;
//        } catch (Exception e) {
//            log.error("Unexpected error processing buy order {}: {}", order.getId(), e.getMessage(), e);
//            throw new OrderProcessingException("Failed to process buy order: " + e.getMessage(), e);
//        }
//    }
//
//    /**
//     * Process a single sell order
//     * @param order the order to process
//     * @return Optional with the processed order
//     */
//    @Override
//    @Transactional
//    @CacheEvict(value = "pendingSellOrders", allEntries = true)
//    public Optional<Order> processSellOrder(Order order) {
//        log.info("Processing sell order: {}", order.getId());
//        User user = order.getUser();
//
//        try {
//            // Step 1: Get detailed order information from Bybit
//            BybitCredentials credentials = bybitCredentialsService.getCredentialsByUser(user);
//            BybitApiResponse<?> orderDetails = bybitApiService.getOrderDetail(order.getId(), credentials);
//
//            if (!orderDetails.isSuccess()) {
//                log.error("Failed to get order details from Bybit: {}", orderDetails.getRetMsg());
//                return Optional.empty();
//            }
//
//            log.info("Retrieved order details from Bybit for order: {}", order.getId());
//
//            // Step 2: Extract counterparty information
//            Map<String, Object> result = (Map<String, Object>) orderDetails.getResult();
//            String counterpartyName = extractCounterpartyName(result);
//            String amount = extractAmount(result);
//
//            log.info("Extracted counterparty name: {} and amount: {} for order {}",
//                    counterpartyName, amount, order.getId());
//
//            // Step 3: Query deposit system for matching deposits
//            Optional<Transaction> matchingDeposit = Optional.ofNullable(
//                    transactionService.findMatchingDeposit(user, order.getAmount(), counterpartyName)
//            );
//
//            if (!matchingDeposit.isPresent()) {
//                log.info("No matching deposit found for order {}", order.getId());
//                return Optional.empty();
//            }
//
//            log.info("Found matching deposit {} for order {}", matchingDeposit.get().getId(), order.getId());
//
//            // Step 4: Release assets on Bybit
//            BybitApiResponse<?> releaseResponse = bybitApiService.releaseAssets(
//                    order.getId(),
//                    credentials
//            );
//
//            if (!releaseResponse.isSuccess()) {
//                log.error("Failed to release assets on Bybit: {}", releaseResponse.getRetMsg());
//                throw new AssetReleaseException("Failed to release assets on Bybit: " + releaseResponse.getRetMsg());
//            }
//
//            log.info("Successfully released assets for order {}", order.getId());
//
//            // Step 5: Send confirmation message
//            bybitApiService.sendChatMessage(
//                    "Thank you for trading with us. We use the Bybit OpenAPI powered by Papay Moni.",
//                    "str",
//                    order.getId(),
//                    credentials
//            );
//            log.info("Sent confirmation message for order {}", order.getId());
//
//            // Step 6: Update order status
//            order.setStatus(70); // Completed status
//            order.setCompletedAt(LocalDateTime.now());
//            Order savedOrder = orderRepository.save(order);
//            log.info("Updated order {} status to 70 (Completed)", order.getId());
//
//            // Step 7: Publish order completed event
//            OrderCompletedEvent event = new OrderCompletedEvent(order.getId(), user.getId());
//            rabbitTemplate.convertAndSend(ORDER_EXCHANGE, ORDER_COMPLETED_KEY, event);
//            eventPublisher.publishEvent(event);
//            log.info("Published OrderCompletedEvent for order {}", order.getId());
//
//            return Optional.of(savedOrder);
//
//        } catch (AssetReleaseException e) {
//            log.error("Asset release error for order {}: {}", order.getId(), e.getMessage());
//            throw e;
//        } catch (Exception e) {
//            log.error("Unexpected error processing sell order {}: {}", order.getId(), e.getMessage(), e);
//            throw new OrderProcessingException("Failed to process sell order: " + e.getMessage(), e);
//        }
//    }
//
//    /**
//     * Extract counterparty name from order details
//     * @param orderDetails the order details from Bybit
//     * @return the counterparty name
//     */
//    private String extractCounterpartyName(Map<String, Object> orderDetails) {
//        return Optional.ofNullable(orderDetails.get("buyer"))
//                .filter(buyer -> buyer instanceof Map)
//                .map(buyer -> (Map<String, Object>) buyer)
//                .map(buyer -> (String) buyer.get("nickName"))
//                .orElse("");
//    }
//
//    /**
//     * Extract amount from order details (without decimal)
//     * @param orderDetails the order details from Bybit
//     * @return the amount as a string without decimal
//     */
//    private String extractAmount(Map<String, Object> orderDetails) {
//        return Optional.ofNullable(orderDetails.get("amount"))
//                .map(Object::toString)
//                .map(amount -> amount.split("\\.")[0])
//                .orElse("");
//    }
//}


package com.papaymoni.middleware.service.impl;

import com.papaymoni.middleware.dto.BybitApiResponse;
import com.papaymoni.middleware.event.OrderCompletedEvent;
import com.papaymoni.middleware.event.OrderPaidEvent;
import com.papaymoni.middleware.exception.AssetReleaseException;
import com.papaymoni.middleware.exception.InsufficientBalanceException;
import com.papaymoni.middleware.exception.OrderProcessingException;
import com.papaymoni.middleware.exception.PaymentProcessingException;
import com.papaymoni.middleware.model.BybitCredentials;
import com.papaymoni.middleware.model.Order;
import com.papaymoni.middleware.model.Transaction;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.repository.OrderRepository;
import com.papaymoni.middleware.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.papaymoni.middleware.config.RabbitMQConfig.*;

/**
 * Implementation of OrderProcessingService
 * Handles the processing of buy and sell orders
 * Implements Java 8 best practices with functional programming, streams, and CompletableFuture
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderProcessingServiceImpl implements OrderProcessingService {

    private final OrderRepository orderRepository;
    private final BybitApiService bybitApiService;
    private final BybitCredentialsService bybitCredentialsService;
    private final PaymentService paymentService;
    private final GLService glService;
    private final BankService bankService;
    private final TransactionService transactionService;
    private final RabbitTemplate rabbitTemplate;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Process pending buy orders (status=10, side=1)
     * @return number of orders processed
     */
    @Override
    @Cacheable(value = "pendingBuyOrders", key = "'processingStatus'", condition = "#result == 0")
    public int processPendingBuyOrders() {
        log.info("Fetching pending buy orders with status=10 and side=1");
        List<Order> pendingOrders = orderRepository.findByStatusAndSide(10, 1);
        log.info("Found {} pending buy orders to process", pendingOrders.size());

        AtomicInteger processedCount = new AtomicInteger(0);

        // Process orders in parallel using Java 8 streams
        pendingOrders.parallelStream()
                .map(this::processBuyOrder)
                .filter(Optional::isPresent)
                .forEach(order -> processedCount.incrementAndGet());

        return processedCount.get();
    }

    /**
     * Process pending sell orders (status=20, side=0)
     * @return number of orders processed
     */
    @Override
    @Cacheable(value = "pendingSellOrders", key = "'processingStatus'", condition = "#result == 0")
    public int processPendingSellOrders() {
        log.info("Fetching pending sell orders with status=20 and side=0");
        List<Order> pendingOrders = orderRepository.findByStatusAndSide(20, 0);
        log.info("Found {} pending sell orders to process", pendingOrders.size());

        AtomicInteger processedCount = new AtomicInteger(0);

        // Process orders in parallel using Java 8 streams
        pendingOrders.parallelStream()
                .map(this::processSellOrder)
                .filter(Optional::isPresent)
                .forEach(order -> processedCount.incrementAndGet());

        return processedCount.get();
    }

    /**
     * Process a single buy order asynchronously
     * @param order the order to process
     * @return CompletableFuture with the processed order
     */
    @Override
    public CompletableFuture<Optional<Order>> processBuyOrderAsync(Order order) {
        return CompletableFuture.supplyAsync(() -> processBuyOrder(order));
    }

    /**
     * Process a single sell order asynchronously
     * @param order the order to process
     * @return CompletableFuture with the processed order
     */
    @Override
    public CompletableFuture<Optional<Order>> processSellOrderAsync(Order order) {
        return CompletableFuture.supplyAsync(() -> processSellOrder(order));
    }

    /**
     * Process a single buy order
     * @param order the order to process
     * @return Optional with the processed order
     */
    @Override
    @Transactional
    @CacheEvict(value = "pendingBuyOrders", allEntries = true)
    public Optional<Order> processBuyOrder(Order order) {
        log.info("Processing buy order: {}", order.getId());
        User user = order.getUser();

        try {
            // Step 1: Get detailed order information from Bybit
            BybitCredentials credentials = bybitCredentialsService.getCredentialsByUser(user);
            BybitApiResponse<?> orderDetails = bybitApiService.getOrderDetail(order.getId(), credentials);

            if (!orderDetails.isSuccess()) {
                log.error("Failed to get order details from Bybit: {}", orderDetails.getRetMsg());
                return Optional.empty();
            }

            log.info("Retrieved order details from Bybit for order: {}", order.getId());

            // Step 2: Check if user has sufficient balance
            // UPDATED: Now passing the currency from the order
            if (!paymentService.hasSufficientBalance(user, order.getAmount(), order.getCurrencyId())) {
                log.error("Insufficient balance for user {} to process order {}", user.getId(), order.getId());
                throw new InsufficientBalanceException("Insufficient balance to process order");
            }

            log.info("User {} has sufficient balance for order {}", user.getId(), order.getId());

            // Step 3: Process payment
            Transaction transaction = paymentService.processBuyOrderPayment(user, order);
            log.info("Created payment transaction {} for order {}", transaction.getId(), order.getId());

            // Step 4: Validate recipient details via name enquiry
            boolean recipientValid = bankService.validateRecipient(
                    order.getTargetNickName(),
                    transaction.getPaymentDetails()
            );

            if (!recipientValid) {
                log.warn("Recipient validation failed for order {}, but proceeding anyway", order.getId());
            }

            // Step 5: Process bank withdrawal
            String transferReference = bankService.processTransfer(
                    transaction.getAmount(),
                    transaction.getPaymentDetails(),
                    order.getTargetNickName(),
                    "Payment for Bybit P2P order " + order.getId()
            );

            transaction.setExternalReference(transferReference);
            log.info("Processed bank transfer with reference {} for order {}", transferReference, order.getId());

            // Step 6: Mark order as paid on Bybit
            BybitApiResponse<?> markPaidResponse = bybitApiService.markOrderAsPaid(
                    order.getId(),
                    transaction.getPaymentMethod(),
                    transaction.getExternalReference(),
                    credentials
            );

            if (!markPaidResponse.isSuccess()) {
                log.error("Failed to mark order as paid on Bybit: {}", markPaidResponse.getRetMsg());
                throw new PaymentProcessingException("Failed to mark order as paid on Bybit: " + markPaidResponse.getRetMsg());
            }

            log.info("Successfully marked order {} as paid on Bybit", order.getId());

            // Step 7: Update order status locally
            order.setStatus(20); // Paid status
            order.setUpdatedAt(LocalDateTime.now());
            Order savedOrder = orderRepository.save(order);
            log.info("Updated order {} status to 20 (Paid)", order.getId());

            // Step 8: Send chat message to seller
            bybitApiService.sendChatMessage(
                    "Hi, payment has been made using Bybit OpenAPI powered by Papay Moni. Kindly check your bank account and release the asset.",
                    "str",
                    order.getId(),
                    credentials
            );
            log.info("Sent chat message to seller for order {}", order.getId());

            // Step 9: Generate and upload receipt if available
            Optional.ofNullable(transaction.getReceiptUrl())
                    .ifPresent(receiptUrl -> {
                        try {
                            byte[] receiptData = paymentService.getReceiptData(transaction);
                            bybitApiService.uploadChatFile(
                                    receiptData,
                                    "payment_receipt.png",
                                    order.getId(),
                                    credentials
                            );
                            log.info("Uploaded payment receipt for order {}", order.getId());
                        } catch (Exception e) {
                            log.warn("Failed to upload receipt for order {}: {}", order.getId(), e.getMessage());
                        }
                    });

            // Step 10: Publish order paid event
            OrderPaidEvent event = new OrderPaidEvent(order.getId(), user.getId());
            rabbitTemplate.convertAndSend(ORDER_EXCHANGE, ORDER_UPDATED_KEY, event);
            eventPublisher.publishEvent(event);
            log.info("Published OrderPaidEvent for order {}", order.getId());

            return Optional.of(savedOrder);

        } catch (InsufficientBalanceException | PaymentProcessingException e) {
            log.error("Error processing buy order {}: {}", order.getId(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error processing buy order {}: {}", order.getId(), e.getMessage(), e);
            throw new OrderProcessingException("Failed to process buy order: " + e.getMessage(), e);
        }
    }

    /**
     * Process a single sell order
     * @param order the order to process
     * @return Optional with the processed order
     */
    @Override
    @Transactional
    @CacheEvict(value = "pendingSellOrders", allEntries = true)
    public Optional<Order> processSellOrder(Order order) {
        log.info("Processing sell order: {}", order.getId());
        User user = order.getUser();

        try {
            // Step 1: Get detailed order information from Bybit
            BybitCredentials credentials = bybitCredentialsService.getCredentialsByUser(user);
            BybitApiResponse<?> orderDetails = bybitApiService.getOrderDetail(order.getId(), credentials);

            if (!orderDetails.isSuccess()) {
                log.error("Failed to get order details from Bybit: {}", orderDetails.getRetMsg());
                return Optional.empty();
            }

            log.info("Retrieved order details from Bybit for order: {}", order.getId());

            // Step 2: Extract counterparty information
            Map<String, Object> result = (Map<String, Object>) orderDetails.getResult();
            String counterpartyName = extractCounterpartyName(result);
            String amount = extractAmount(result);

            log.info("Extracted counterparty name: {} and amount: {} for order {}",
                    counterpartyName, amount, order.getId());

            // Step 3: Query deposit system for matching deposits
            Optional<Transaction> matchingDeposit = Optional.ofNullable(
                    transactionService.findMatchingDeposit(user, order.getAmount(), counterpartyName)
            );

            if (!matchingDeposit.isPresent()) {
                log.info("No matching deposit found for order {}", order.getId());
                return Optional.empty();
            }

            log.info("Found matching deposit {} for order {}", matchingDeposit.get().getId(), order.getId());

            // Step 4: Release assets on Bybit
            BybitApiResponse<?> releaseResponse = bybitApiService.releaseAssets(
                    order.getId(),
                    credentials
            );

            if (!releaseResponse.isSuccess()) {
                log.error("Failed to release assets on Bybit: {}", releaseResponse.getRetMsg());
                throw new AssetReleaseException("Failed to release assets on Bybit: " + releaseResponse.getRetMsg());
            }

            log.info("Successfully released assets for order {}", order.getId());

            // Step 5: Send confirmation message
            bybitApiService.sendChatMessage(
                    "Thank you for trading with us. We use the Bybit OpenAPI powered by Papay Moni.",
                    "str",
                    order.getId(),
                    credentials
            );
            log.info("Sent confirmation message for order {}", order.getId());

            // Step 6: Update order status
            order.setStatus(70); // Completed status
            order.setCompletedAt(LocalDateTime.now());
            Order savedOrder = orderRepository.save(order);
            log.info("Updated order {} status to 70 (Completed)", order.getId());

            // Step 7: Publish order completed event
            OrderCompletedEvent event = new OrderCompletedEvent(order.getId(), user.getId());
            rabbitTemplate.convertAndSend(ORDER_EXCHANGE, ORDER_COMPLETED_KEY, event);
            eventPublisher.publishEvent(event);
            log.info("Published OrderCompletedEvent for order {}", order.getId());

            return Optional.of(savedOrder);

        } catch (AssetReleaseException e) {
            log.error("Asset release error for order {}: {}", order.getId(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error processing sell order {}: {}", order.getId(), e.getMessage(), e);
            throw new OrderProcessingException("Failed to process sell order: " + e.getMessage(), e);
        }
    }

    /**
     * Extract counterparty name from order details
     * @param orderDetails the order details from Bybit
     * @return the counterparty name
     */
    private String extractCounterpartyName(Map<String, Object> orderDetails) {
        return Optional.ofNullable(orderDetails.get("buyer"))
                .filter(buyer -> buyer instanceof Map)
                .map(buyer -> (Map<String, Object>) buyer)
                .map(buyer -> (String) buyer.get("nickName"))
                .orElse("");
    }

    /**
     * Extract amount from order details (without decimal)
     * @param orderDetails the order details from Bybit
     * @return the amount as a string without decimal
     */
    private String extractAmount(Map<String, Object> orderDetails) {
        return Optional.ofNullable(orderDetails.get("amount"))
                .map(Object::toString)
                .map(amount -> amount.split("\\.")[0])
                .orElse("");
    }
}
