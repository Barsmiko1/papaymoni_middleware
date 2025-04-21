package com.papaymoni.middleware.controller;

import com.papaymoni.middleware.dto.ApiResponse;
import com.papaymoni.middleware.model.Transaction;
import com.papaymoni.middleware.model.VirtualAccount;
import com.papaymoni.middleware.service.PaymentService;
import com.papaymoni.middleware.service.VirtualAccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/webhook")
public class WebhookController {

    private final VirtualAccountService virtualAccountService;
    private final PaymentService paymentService;

    public WebhookController(VirtualAccountService virtualAccountService,
                             PaymentService paymentService) {
        this.virtualAccountService = virtualAccountService;
        this.paymentService = paymentService;
    }

    @PostMapping("/deposit")
    public ResponseEntity<ApiResponse<Transaction>> handleDepositWebhook(@RequestBody Map<String, String> payload) {
        // Validate webhook signature
        // In a real implementation, you would verify the webhook signature

        String accountNumber = payload.get("accountNumber");
        BigDecimal amount = new BigDecimal(payload.get("amount"));
        String reference = payload.get("reference");

        // Find the virtual account
        VirtualAccount virtualAccount = virtualAccountService.getVirtualAccountByAccountNumber(accountNumber);

        // Process the deposit
        Transaction transaction = paymentService.processDeposit(virtualAccount, amount, reference);

        return ResponseEntity.ok(ApiResponse.success("Deposit processed successfully", transaction));
    }
}
