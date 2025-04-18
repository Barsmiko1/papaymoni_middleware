package com.papaymoni.middleware.service;

import com.papaymoni.middleware.model.Order;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for processing orders
 * Follows Java 8 best practices with Optional and CompletableFuture
 */
public interface OrderProcessingService {

    /**
     * Process pending buy orders (status=10, side=1)
     * @return number of orders processed
     */
    int processPendingBuyOrders();

    /**
     * Process pending sell orders (status=20, side=0)
     * @return number of orders processed
     */
    int processPendingSellOrders();

    /**
     * Process a single buy order asynchronously
     * @param order the order to process
     * @return CompletableFuture with the processed order
     */
    CompletableFuture<Optional<Order>> processBuyOrderAsync(Order order);

    /**
     * Process a single sell order asynchronously
     * @param order the order to process
     * @return CompletableFuture with the processed order
     */
    CompletableFuture<Optional<Order>> processSellOrderAsync(Order order);

    /**
     * Process a single buy order
     * @param order the order to process
     * @return Optional with the processed order
     */
    Optional<Order> processBuyOrder(Order order);

    /**
     * Process a single sell order
     * @param order the order to process
     * @return Optional with the processed order
     */
    Optional<Order> processSellOrder(Order order);
}
