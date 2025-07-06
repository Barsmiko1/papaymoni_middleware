package com.papaymoni.middleware.service.impl;

import com.papaymoni.middleware.dto.ExchangeRateDto;
import com.papaymoni.middleware.dto.ExchangeRequestDto;
import com.papaymoni.middleware.dto.ExchangeResultDto;
import com.papaymoni.middleware.exception.InsufficientBalanceException;
import com.papaymoni.middleware.exception.ResourceNotFoundException;
import com.papaymoni.middleware.model.CurrencyExchange;
import com.papaymoni.middleware.model.Transaction;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.model.WalletBalance;
import com.papaymoni.middleware.model.Currency;
import com.papaymoni.middleware.repository.CurrencyExchangeRepository;
import com.papaymoni.middleware.repository.WalletBalanceRepository;
import com.papaymoni.middleware.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CurrencyExchangeServiceImpl implements CurrencyExchangeService {

    private final ExchangeRateService exchangeRateService;
    private final WalletBalanceService walletBalanceService;
    private final CurrencyService currencyService;
    private final CurrencyExchangeRepository currencyExchangeRepository;
    private final TransactionService transactionService;
    private final GLService glService;
    private final ReceiptService receiptService;
    private final NotificationService notificationService;
    private final WalletBalanceRepository walletBalanceRepository;

    private static final List<String> SUPPORTED_CURRENCIES = Arrays.asList("USD", "EUR", "GBP", "CAD", "NGN");

    @Override
    public ExchangeRateDto getExchangeRates(String baseCurrency) {
        if (baseCurrency == null || baseCurrency.trim().isEmpty()) {
            throw new IllegalArgumentException("Base currency cannot be null or empty");
        }

        if (!SUPPORTED_CURRENCIES.contains(baseCurrency)) {
            throw new IllegalArgumentException("Unsupported base currency: " + baseCurrency);
        }

        List<String> targetCurrencies = SUPPORTED_CURRENCIES.stream()
                .filter(c -> !c.equals(baseCurrency))
                .collect(Collectors.toList());

        return exchangeRateService.getExchangeRates(baseCurrency, targetCurrencies);
    }

    @Override
    @Transactional
    public ExchangeResultDto exchangeCurrency(User user, ExchangeRequestDto request, String ipAddress) {
        // Log initial parameters and validate user
        log.info("exchangeCurrency called with request: {}, ipAddress: {}", request, ipAddress);

        if (user == null) {
            log.error("User is null in exchangeCurrency method");
            throw new IllegalArgumentException("User cannot be null");
        }

        log.info("Processing exchange for User ID: {}, Username: {}", user.getId(), user.getUsername());

        // Validate request
        if (request == null) {
            throw new IllegalArgumentException("Exchange request cannot be null");
        }

        if (request.getFromCurrency() == null || request.getFromCurrency().trim().isEmpty()) {
            throw new IllegalArgumentException("From currency cannot be null or empty");
        }

        if (request.getToCurrency() == null || request.getToCurrency().trim().isEmpty()) {
            throw new IllegalArgumentException("To currency cannot be null or empty");
        }

        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        // Validate currencies
        if (!SUPPORTED_CURRENCIES.contains(request.getFromCurrency()) ||
                !SUPPORTED_CURRENCIES.contains(request.getToCurrency())) {
            throw new IllegalArgumentException("Unsupported currency");
        }

        if (request.getFromCurrency().equals(request.getToCurrency())) {
            throw new IllegalArgumentException("From and To currencies must be different");
        }

        // Get exchange rate
        log.debug("Fetching exchange rate from {} to {}", request.getFromCurrency(), request.getToCurrency());
        BigDecimal rate = exchangeRateService.getExchangeRate(request.getFromCurrency(), request.getToCurrency());
        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Invalid exchange rate received");
        }

        // Calculate fee
        BigDecimal fee = exchangeRateService.calculateFee(request.getAmount());
        if (fee == null) {
            fee = BigDecimal.ZERO;
        }

        // Calculate total amount to deduct from source wallet
        BigDecimal totalDeduction = request.getAmount().add(fee);

        // Calculate amount to credit to destination wallet
        BigDecimal amountToCredit = request.getAmount().multiply(rate).setScale(6, RoundingMode.HALF_UP);

        log.info("Exchange request: {} {} to {} {}, rate: {}, fee: {}, total deduction: {}",
                request.getAmount(), request.getFromCurrency(),
                amountToCredit, request.getToCurrency(),
                rate, fee, totalDeduction);

        // Get source currency
        Currency sourceCurrency = currencyService.getCurrencyEntityByCode(request.getFromCurrency());
        if (sourceCurrency == null) {
            throw new ResourceNotFoundException("Source currency not found: " + request.getFromCurrency());
        }

        log.debug("Source currency found - ID: {}, Code: {}", sourceCurrency.getId(), sourceCurrency.getCode());

        // Get destination currency
        Currency destCurrency = currencyService.getCurrencyEntityByCode(request.getToCurrency());
        if (destCurrency == null) {
            throw new ResourceNotFoundException("Destination currency not found: " + request.getToCurrency());
        }

        log.debug("Destination currency found - ID: {}, Code: {}", destCurrency.getId(), destCurrency.getCode());

        // Ensure wallets exist
        WalletBalance sourceWallet = walletBalanceService.getOrCreateWalletBalance(user, sourceCurrency);
        log.debug("Source wallet found - ID: {}, Available: {}",
                sourceWallet.getId(), sourceWallet.getAvailableBalance());

        WalletBalance destWallet = walletBalanceService.getOrCreateWalletBalance(user, destCurrency);
        log.debug("Destination wallet found - ID: {}, Available: {}",
                destWallet.getId(), destWallet.getAvailableBalance());

        // Check available balance
        BigDecimal availableBalance = walletBalanceService.getAvailableBalance(user, request.getFromCurrency());
        log.info("User (ID: {}) available balance in {}: {}",
                user.getId(), request.getFromCurrency(), availableBalance);

        if (availableBalance.compareTo(totalDeduction) < 0) {
            log.warn("Insufficient balance for exchange. Required: {}, Available: {}",
                    totalDeduction, availableBalance);
            throw new InsufficientBalanceException("Insufficient balance for exchange");
        }

        // Generate reference ID
        String referenceId = "EX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.debug("Generated reference ID: {}", referenceId);

        // Create exchange record
        CurrencyExchange exchange = new CurrencyExchange();
        exchange.setUser(user);
        exchange.setFromCurrency(request.getFromCurrency());
        exchange.setToCurrency(request.getToCurrency());
        exchange.setFromAmount(request.getAmount());
        exchange.setToAmount(amountToCredit);
        exchange.setRate(rate);
        exchange.setFee(fee);
        exchange.setStatus("PENDING");
        exchange.setReferenceId(referenceId);
        exchange.setCreatedBy(user.getUsername());
        exchange.setIpAddress(ipAddress);
        exchange.setCreatedAt(LocalDateTime.now());

        CurrencyExchange savedExchange = currencyExchangeRepository.save(exchange);
        log.debug("Saved exchange record with ID: {}", savedExchange.getId());

        try {
            // Debit source wallet
            log.debug("Debiting source wallet");
            walletBalanceService.debitWallet(
                    user,
                    request.getFromCurrency(),
                    totalDeduction,
                    "Currency exchange: " + request.getFromCurrency() + " to " + request.getToCurrency(),
                    referenceId,
                    "CURRENCY_EXCHANGE",
                    user.getUsername(),
                    ipAddress
            );

            // Credit destination wallet
            log.debug("Crediting destination wallet");
            walletBalanceService.creditWallet(
                    user,
                    request.getToCurrency(),
                    amountToCredit,
                    "Currency exchange from " + request.getFromCurrency(),
                    referenceId,
                    "CURRENCY_EXCHANGE",
                    user.getUsername(),
                    ipAddress
            );

            // Create transaction record for the exchange
            log.debug("Creating transaction record");
            Transaction transaction = new Transaction();
            transaction.setUser(user);
            transaction.setTransactionType("EXCHANGE");
            transaction.setAmount(request.getAmount());
            transaction.setFee(fee);
            transaction.setCurrency(request.getFromCurrency());
            transaction.setStatus("COMPLETED");
            transaction.setExternalReference(referenceId);
            transaction.setPaymentMethod("WALLET");
            transaction.setPaymentDetails("Exchange from " + request.getFromCurrency() + " to " + request.getToCurrency());
            transaction.setCreatedAt(LocalDateTime.now());
            transaction.setCompletedAt(LocalDateTime.now());

            Transaction savedTransaction = transactionService.save(transaction);
            log.debug("Saved transaction record with ID: {}", savedTransaction.getId());

            // Update GL entries
            log.debug("Updating GL entries");
            glService.createEntry(
                    user,
                    "DEBIT",
                    "USER",
                    totalDeduction,
                    request.getFromCurrency(),
                    "Currency exchange debit",
                    savedTransaction
            );

            glService.createEntry(
                    user,
                    "CREDIT",
                    "USER",
                    amountToCredit,
                    request.getToCurrency(),
                    "Currency exchange credit",
                    savedTransaction
            );

            // Credit fee to platform account
            glService.creditFeeAccount(fee);

            // Generate receipt
            log.debug("Generating receipt");
            String receiptUrl = receiptService.generateReceipt(savedTransaction);
            savedTransaction.setReceiptUrl(receiptUrl);
            transactionService.save(savedTransaction);

            // Update exchange record
            log.debug("Updating exchange record status to COMPLETED");
            savedExchange.setStatus("COMPLETED");
            savedExchange.setCompletedAt(LocalDateTime.now());
            savedExchange.setReceiptUrl(receiptUrl);
            currencyExchangeRepository.save(savedExchange);

            // Create in-app notification
            log.debug("Creating in-app notification");
            notificationService.createNotification(
                    user,
                    "APP",
                    "Currency Exchange Completed",
                    "Your currency exchange of " + request.getAmount() + " " + request.getFromCurrency() +
                            " to " + amountToCredit + " " + request.getToCurrency() + " has been completed successfully."
            );

            // Prepare result
            ExchangeResultDto result = new ExchangeResultDto();
            result.setFromCurrency(request.getFromCurrency());
            result.setToCurrency(request.getToCurrency());
            result.setFromAmount(request.getAmount());
            result.setToAmount(amountToCredit);
            result.setRate(rate);
            result.setFee(fee);
            result.setTotalDeducted(totalDeduction);
            result.setReceiptUrl(receiptUrl);
            result.setExchangedAt(LocalDateTime.now());

            log.info("Currency exchange completed successfully for User ID: {}, Reference ID: {}",
                    user.getId(), referenceId);

            return result;

        } catch (Exception e) {
            // If any error occurs, update exchange status to FAILED
            log.error("Error processing currency exchange for User ID: {}, Reference ID: {}",
                    user.getId(), referenceId, e);

            savedExchange.setStatus("FAILED");
            currencyExchangeRepository.save(savedExchange);
            throw e;
        }
    }

    @Override
    public List<ExchangeResultDto> getRecentExchanges(User user) {
        if (user == null) {
            log.error("User is null in getRecentExchanges method");
            throw new IllegalArgumentException("User cannot be null");
        }

        log.debug("Fetching recent exchanges for user ID: {}", user.getId());

        return currencyExchangeRepository.findTop10ByUserOrderByCreatedAtDesc(user).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    private ExchangeResultDto convertToDto(CurrencyExchange exchange) {
        if (exchange == null) {
            return null;
        }

        ExchangeResultDto dto = new ExchangeResultDto();
        dto.setFromCurrency(exchange.getFromCurrency());
        dto.setToCurrency(exchange.getToCurrency());
        dto.setFromAmount(exchange.getFromAmount());
        dto.setToAmount(exchange.getToAmount());
        dto.setRate(exchange.getRate());
        dto.setFee(exchange.getFee());
        dto.setTotalDeducted(exchange.getFromAmount().add(exchange.getFee()));
        dto.setReceiptUrl(exchange.getReceiptUrl());
        dto.setExchangedAt(exchange.getCompletedAt() != null ? exchange.getCompletedAt() : exchange.getCreatedAt());

        return dto;
    }
}