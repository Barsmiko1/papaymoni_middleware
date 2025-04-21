package com.papaymoni.middleware.controller;

import com.papaymoni.middleware.dto.ApiResponse;
import com.papaymoni.middleware.service.OrderProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

/**
 * Controller for manually triggering order processing
 * Implements asynchronous processing using CompletableFuture
 */
@Slf4j
@RestController
@RequestMapping("/api/order-processing")
@RequiredArgsConstructor
public class OrderProcessingController {

    private final OrderProcessingService orderProcessingService;

    /**
     * Manually trigger processing of pending buy orders
     * @return the number of orders processed
     */
    @PostMapping("/buy-orders")
    @PreAuthorize("hasRole('ADMIN')")
    public CompletableFuture<ResponseEntity<ApiResponse<Integer>>> processPendingBuyOrders() {
        log.info("Manual trigger of buy order processing");

        return CompletableFuture.supplyAsync(() -> {
            int processedCount = orderProcessingService.processPendingBuyOrders();
            log.info("Manually processed {} buy orders", processedCount);
            return ResponseEntity.ok(ApiResponse.success("Processed " + processedCount + " buy orders", processedCount));
        });
    }

    /**
     * Manually trigger processing of pending sell orders
     * @return the number of orders processed
     */
    @PostMapping("/sell-orders")
    @PreAuthorize("hasRole('ADMIN')")
    public CompletableFuture<ResponseEntity<ApiResponse<Integer>>> processPendingSellOrders() {
        log.info("Manual trigger of sell order processing");

        return CompletableFuture.supplyAsync(() -> {
            int processedCount = orderProcessingService.processPendingSellOrders();
            log.info("Manually processed {} sell orders", processedCount);
            return ResponseEntity.ok(ApiResponse.success("Processed " + processedCount + " sell orders", processedCount));
        });
    }

    /**
     * Manually trigger processing of all pending orders
     * @return the total number of orders processed
     */
    @PostMapping("/all-orders")
    @PreAuthorize("hasRole('ADMIN')")
    public CompletableFuture<ResponseEntity<ApiResponse<Integer>>> processAllPendingOrders() {
        log.info("Manual trigger of all order processing");

        CompletableFuture<Integer> buyOrdersFuture = CompletableFuture.supplyAsync(
                orderProcessingService::processPendingBuyOrders
        );

        CompletableFuture<Integer> sellOrdersFuture = CompletableFuture.supplyAsync(
                orderProcessingService::processPendingSellOrders
        );

        return buyOrdersFuture.thenCombine(sellOrdersFuture, (buyCount, sellCount) -> {
            int totalCount = buyCount + sellCount;
            log.info("Manually processed {} total orders ({} buy, {} sell)", totalCount, buyCount, sellCount);
            return ResponseEntity.ok(ApiResponse.success(
                    "Processed " + totalCount + " total orders (" + buyCount + " buy, " + sellCount + " sell)",
                    totalCount
            ));
        });
    }
}
