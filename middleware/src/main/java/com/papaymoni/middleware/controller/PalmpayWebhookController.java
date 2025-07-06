//package com.papaymoni.middleware.controller;
//import com.papaymoni.middleware.service.PalmpayPayoutGatewayService;
//import com.papaymoni.middleware.dto.ApiResponse;
//import com.papaymoni.middleware.dto.PalmpayPayinWebhookDto;
//import com.papaymoni.middleware.dto.PalmpayPayoutWebhookDto;
//import com.papaymoni.middleware.service.PalmpayWebhookService;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.PostMapping;
//import org.springframework.web.bind.annotation.RequestBody;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//
//@Slf4j
//@RestController
//@RequestMapping("/api/webhook/palmpay")
//@RequiredArgsConstructor
//public class PalmpayWebhookController {
//
//    private final PalmpayWebhookService palmpayWebhookService;
//    private final PalmpayPayoutGatewayService palmpayPayoutGatewayService;
//
//    /**
//     * Handle Palmpay Pay-in notifications
//     * According to documentation, must return "success" string (not JSON)
//     */
//    @PostMapping("/pay-in")
//    public ResponseEntity<String> handlePayinWebhook(@RequestBody PalmpayPayinWebhookDto webhookDto) {
//        log.info("Received Palmpay pay-in webhook notification: {}", webhookDto.getOrderNo());
//
//        try {
//            // Process the webhook notification
//            boolean processed = palmpayWebhookService.processPayinWebhook(webhookDto);
//
//            if (processed) {
//                // Return the exact string "success" as required by Palmpay
//                return ResponseEntity.ok("success");
//            } else {
//                // Log failure but still return 200 to prevent retries as we'll handle it internally
//                log.error("Failed to process webhook, but returning success to avoid retries");
//                return ResponseEntity.ok("success");
//            }
//        } catch (Exception e) {
//            // Log the error for internal monitoring
//            log.error("Error processing Palmpay webhook: {}", e.getMessage(), e);
//
//            // Still return success to prevent Palmpay retries - we'll handle retries internally
//            return ResponseEntity.ok("success");
//        }
//    }
//
//    /**
//     * Handle Palmpay Pay-out notifications
//     * According to documentation, must return "success" string (not JSON)
//     */
//    @PostMapping("/pay-out")
//    public ResponseEntity<String> handlePayoutWebhook(@RequestBody PalmpayPayoutWebhookDto webhookDto) {
//        log.info("Received Palmpay pay-out webhook notification for orderId: {}, status: {}",
//                webhookDto.getOrderId(), webhookDto.getOrderStatus());
//
//        try {
//            // Process the webhook notification
//            boolean processed = palmpayPayoutGatewayService.processPayoutWebhook(webhookDto);
//
//            if (processed) {
//                // Return the exact string "success" as required by Palmpay
//                return ResponseEntity.ok("success");
//            } else {
//                // Log failure but still return 200 to prevent retries as we'll handle it internally
//                log.error("Failed to process payout webhook, but returning success to avoid retries");
//                return ResponseEntity.ok("success");
//            }
//        } catch (Exception e) {
//            // Log the error for internal monitoring
//            log.error("Error processing Palmpay payout webhook: {}", e.getMessage(), e);
//
//            // Still return success to prevent Palmpay retries - we'll handle retries internally
//            return ResponseEntity.ok("success");
//        }
//    }
//}


package com.papaymoni.middleware.controller;

import com.papaymoni.middleware.dto.ApiResponse;
import com.papaymoni.middleware.dto.PalmpayPayinWebhookDto;
import com.papaymoni.middleware.dto.PalmpayPayoutWebhookDto;
import com.papaymoni.middleware.service.PalmpayPayoutGatewayService;
import com.papaymoni.middleware.service.PalmpayWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/webhook/palmpay")
@RequiredArgsConstructor
public class PalmpayWebhookController {

    private final PalmpayWebhookService palmpayWebhookService;
    private final PalmpayPayoutGatewayService palmpayPayoutGatewayService;

    /**
     * Handle Palmpay Pay-in notifications
     * According to documentation, must return "success" string (not JSON)
     */
    @PostMapping("/pay-in")
    public ResponseEntity<String> handlePayinWebhook(@RequestBody PalmpayPayinWebhookDto webhookDto) {
        log.info("Received Palmpay pay-in webhook notification: {}", webhookDto.getOrderNo());

        try {
            // Process the webhook notification
            boolean processed = palmpayWebhookService.processPayinWebhook(webhookDto);

            if (processed) {
                // Return the exact string "success" as required by Palmpay
                return ResponseEntity.ok("success");
            } else {
                // Log failure but still return 200 to prevent retries as we'll handle it internally
                log.error("Failed to process webhook, but returning success to avoid retries");
                return ResponseEntity.ok("success");
            }
        } catch (Exception e) {
            // Log the error for internal monitoring
            log.error("Error processing Palmpay webhook: {}", e.getMessage(), e);

            // Still return success to prevent Palmpay retries - we'll handle retries internally
            return ResponseEntity.ok("success");
        }
    }

    /**
     * Handle Palmpay Pay-out notifications
     * According to documentation, must return "success" string (not JSON)
     */
    @PostMapping("/pay-out")
    public ResponseEntity<String> handlePayoutWebhook(@RequestBody PalmpayPayoutWebhookDto webhookDto) {
        log.info("Received Palmpay pay-out webhook notification for orderId: {}, status: {}",
                webhookDto.getOrderId(), webhookDto.getOrderStatus());

        try {
            // Process the webhook notification
            boolean processed = palmpayPayoutGatewayService.processPayoutWebhook(webhookDto);

            if (processed) {
                // Return the exact string "success" as required by Palmpay
                return ResponseEntity.ok("success");
            } else {
                // Log failure but still return 200 to prevent retries as we'll handle it internally
                log.error("Failed to process payout webhook, but returning success to avoid retries");
                return ResponseEntity.ok("success");
            }
        } catch (Exception e) {
            // Log the error for internal monitoring
            log.error("Error processing Palmpay payout webhook: {}", e.getMessage(), e);

            // Still return success to prevent Palmpay retries - we'll handle retries internally
            return ResponseEntity.ok("success");
        }
    }
}

