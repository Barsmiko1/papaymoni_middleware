//package com.papaymoni.middleware.scheduler;
//
//import com.papaymoni.middleware.service.OrderProcessingService;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Component;
//
///**
// * Scheduler for processing sell orders (buy orders on Bybit)
// * Runs every 20 seconds to check for orders with status 20 (waiting for seller release)
// * and order trade type side = 0 (Buy on Bybit)
// */
//@Slf4j
//@Component
//@RequiredArgsConstructor
//public class SellOrderProcessingScheduler {
//
//    private final OrderProcessingService orderProcessingService;
//
//    /**
//     * Process pending sell orders (status=20, side=0)
//     * Runs every 20 seconds
//     */
//    @Scheduled(fixedDelay = 20000)
//    public void processPendingSellOrders() {
//        log.info("Starting scheduled sell order processing job");
//        try {
//            int processedCount = orderProcessingService.processPendingSellOrders();
//            log.info("Completed sell order processing job. Processed {} orders", processedCount);
//        } catch (Exception e) {
//            log.error("Error in sell order processing job", e);
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
 * Scheduler for processing sell orders (buy orders on Bybit)
 * Runs every 20 seconds to check for orders with status 20 (waiting for seller release)
 * and order trade type side = 0 (Buy on Bybit)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SellOrderProcessingScheduler {

    private final OrderProcessingService orderProcessingService;
    private final SchedulerStatus schedulerStatus;

    /**
     * Process pending sell orders (status=20, side=0)
     * Runs every 20 seconds if scheduler is enabled
     */
    @Scheduled(fixedDelayString = "${app.scheduler.sell-order-processing.delay}")
    public void processPendingSellOrders() {
        if (!schedulerStatus.isEnabled()) {
            log.debug("Scheduler is disabled. Skipping sell order processing.");
            return;
        }

        log.info("Starting scheduled sell order processing job");
        try {
            int processedCount = orderProcessingService.processPendingSellOrders();
            log.info("Completed sell order processing job. Processed {} orders", processedCount);
        } catch (Exception e) {
            log.error("Error in sell order processing job", e);
        }
    }
}

