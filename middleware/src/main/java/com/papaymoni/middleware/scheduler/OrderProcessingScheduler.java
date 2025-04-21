package com.papaymoni.middleware.scheduler;

import com.papaymoni.middleware.service.OrderProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Unified scheduler for processing both buy and sell orders
 * Implements asynchronous processing using CompletableFuture
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderProcessingScheduler {

    private final OrderProcessingService orderProcessingService;

    /**
     * Process pending buy orders (status=10, side=1)
     * Runs every 30 seconds
     */
    @Scheduled(fixedDelay = 30000)
    public void processPendingBuyOrders() {
        log.info("Starting scheduled buy order processing job");
        CompletableFuture.supplyAsync(() -> {
            try {
                return orderProcessingService.processPendingBuyOrders();
            } catch (Exception e) {
                log.error("Error in buy order processing job", e);
                return 0;
            }
        }).thenAccept(processedCount ->
                log.info("Completed buy order processing job. Processed {} orders", processedCount)
        );
    }

    /**
     * Process pending sell orders (status=20, side=0)
     * Runs every 20 seconds
     */
    @Scheduled(fixedDelay = 20000)
    public void processPendingSellOrders() {
        log.info("Starting scheduled sell order processing job");
        CompletableFuture.supplyAsync(() -> {
            try {
                return orderProcessingService.processPendingSellOrders();
            } catch (Exception e) {
                log.error("Error in sell order processing job", e);
                return 0;
            }
        }).thenAccept(processedCount ->
                log.info("Completed sell order processing job. Processed {} orders", processedCount)
        );
    }
}
