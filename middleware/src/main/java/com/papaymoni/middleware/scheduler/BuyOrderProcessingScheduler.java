//package com.papaymoni.middleware.scheduler;
//
//import com.papaymoni.middleware.service.OrderProcessingService;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Component;
//
///**
// * Scheduler for processing buy orders (sell orders on Bybit)
// * Runs every 30 seconds to check for orders with status 10 (waiting for buy pay)
// */
//@Slf4j
//@Component
//@RequiredArgsConstructor
//public class BuyOrderProcessingScheduler {
//
//    private final OrderProcessingService orderProcessingService;
//
//    /**
//     * Process pending buy orders (status=10, side=1)
//     * Runs every 30 seconds
//     */
//    @Scheduled(fixedDelay = 30000)
//    public void processPendingBuyOrders() {
//        log.info("Starting scheduled buy order processing job");
//        try {
//            int processedCount = orderProcessingService.processPendingBuyOrders();
//            log.info("Completed buy order processing job. Queued {} orders for processing", processedCount);
//        } catch (Exception e) {
//            log.error("Error in buy order processing job", e);
//        }
//    }
//}

package com.papaymoni.middleware.scheduler;

import com.papaymoni.middleware.config.SchedulerConfig.SchedulerStatus;
import com.papaymoni.middleware.service.OrderProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler for processing buy orders (sell orders on Bybit)
 * Runs every 30 seconds to check for orders with status 10 (waiting for buy pay)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BuyOrderProcessingScheduler {

    private final OrderProcessingService orderProcessingService;
    private final SchedulerStatus schedulerStatus;

    /**
     * Process pending buy orders (status=10, side=1)
     * Runs every 30 seconds if scheduler is enabled
     */
    @Scheduled(fixedDelayString = "${app.scheduler.buy-order-processing.delay}")
    public void processPendingBuyOrders() {
        if (!schedulerStatus.isEnabled()) {
            log.debug("Scheduler is disabled. Skipping buy order processing.");
            return;
        }

        log.info("Starting scheduled buy order processing job");
        try {
            int processedCount = orderProcessingService.processPendingBuyOrders();
            log.info("Completed buy order processing job. Queued {} orders for processing", processedCount);
        } catch (Exception e) {
            log.error("Error in buy order processing job", e);
        }
    }
}

