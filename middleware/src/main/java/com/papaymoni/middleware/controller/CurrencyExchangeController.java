//package com.papaymoni.middleware.controller;
//
//import com.papaymoni.middleware.dto.ApiResponse;
//import com.papaymoni.middleware.dto.ExchangeRateDto;
//import com.papaymoni.middleware.dto.ExchangeRequestDto;
//import com.papaymoni.middleware.dto.ExchangeResultDto;
//import com.papaymoni.middleware.exception.InsufficientBalanceException;
//import com.papaymoni.middleware.model.User;
//import com.papaymoni.middleware.security.CurrentUser;
//import com.papaymoni.middleware.service.CurrencyExchangeService;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//import javax.servlet.http.HttpServletRequest;
//import javax.validation.Valid;
//import java.util.List;
//
//@Slf4j
//@RestController
//@RequestMapping("/api/exchange")
//@RequiredArgsConstructor
//public class CurrencyExchangeController {
//
//    private final CurrencyExchangeService currencyExchangeService;
//
//    @GetMapping("/rates/{baseCurrency}")
//    public ResponseEntity<ApiResponse> getExchangeRates(@PathVariable String baseCurrency) {
//        try {
//            ExchangeRateDto rates = currencyExchangeService.getExchangeRates(baseCurrency.toUpperCase());
//            return ResponseEntity.ok(ApiResponse.success("Exchange rates retrieved successfully", rates));
//        } catch (IllegalArgumentException e) {
//            return ResponseEntity.badRequest()
//                    .body(ApiResponse.error(e.getMessage()));
//        } catch (Exception e) {
//            log.error("Error retrieving exchange rates", e);
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                    .body(ApiResponse.error("Failed to retrieve exchange rates"));
//        }
//    }
//
//    @PostMapping
//    public ResponseEntity<ApiResponse> exchangeCurrency(
//            @CurrentUser User user,
//            @Valid @RequestBody ExchangeRequestDto request,
//            HttpServletRequest httpRequest) {
//
//        try {
//            // Get client IP address
//            String ipAddress = httpRequest.getRemoteAddr();
//
//            // Process exchange
//            ExchangeResultDto result = currencyExchangeService.exchangeCurrency(user, request, ipAddress);
//
//            return ResponseEntity.ok(ApiResponse.success("Currency exchange completed successfully", result));
//        } catch (InsufficientBalanceException e) {
//            return ResponseEntity.badRequest()
//                    .body(ApiResponse.error(e.getMessage()));
//        } catch (IllegalArgumentException e) {
//            return ResponseEntity.badRequest()
//                    .body(ApiResponse.error(e.getMessage()));
//        } catch (Exception e) {
//            log.error("Error processing currency exchange", e);
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                    .body(ApiResponse.error("Failed to process currency exchange"));
//        }
//    }
//
//    @GetMapping("/history")
//    public ResponseEntity<ApiResponse> getExchangeHistory(@CurrentUser User user) {
//        try {
//            List<ExchangeResultDto> history = currencyExchangeService.getRecentExchanges(user);
//            return ResponseEntity.ok(ApiResponse.success("Exchange history retrieved successfully", history));
//        } catch (Exception e) {
//            log.error("Error retrieving exchange history", e);
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                    .body(ApiResponse.error("Failed to retrieve exchange history"));
//        }
//    }
//}


package com.papaymoni.middleware.controller;

import com.papaymoni.middleware.dto.ApiResponse;
import com.papaymoni.middleware.dto.ExchangeRateDto;
import com.papaymoni.middleware.dto.ExchangeRequestDto;
import com.papaymoni.middleware.dto.ExchangeResultDto;
import com.papaymoni.middleware.exception.InsufficientBalanceException;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.security.CurrentUser;
import com.papaymoni.middleware.service.CurrencyExchangeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/exchange")
@RequiredArgsConstructor
public class CurrencyExchangeController {

    private final CurrencyExchangeService currencyExchangeService;

    @GetMapping("/rates/{baseCurrency}")
    public ResponseEntity<ApiResponse> getExchangeRates(@PathVariable String baseCurrency) {
        try {
            ExchangeRateDto rates = currencyExchangeService.getExchangeRates(baseCurrency.toUpperCase());
            return ResponseEntity.ok(ApiResponse.success("Exchange rates retrieved successfully", rates));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error retrieving exchange rates", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve exchange rates"));
        }
    }

    @PostMapping
    public ResponseEntity<ApiResponse> exchangeCurrency(
            @CurrentUser User user,
            @Valid @RequestBody ExchangeRequestDto request,
            HttpServletRequest httpRequest) {

        // Add null check for user
        if (user == null) {
            log.error("User is null in exchangeCurrency controller method");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Authentication required"));
        }

        try {
            // Get client IP address
            String ipAddress = httpRequest.getRemoteAddr();

            // Log the request information for debugging
            log.info("Processing exchange for User ID: {}, Username: {}, From: {}, To: {}, Amount: {}",
                    user.getId(), user.getUsername(), request.getFromCurrency(),
                    request.getToCurrency(), request.getAmount());

            // Process exchange
            ExchangeResultDto result = currencyExchangeService.exchangeCurrency(user, request, ipAddress);

            return ResponseEntity.ok(ApiResponse.success("Currency exchange completed successfully", result));
        } catch (InsufficientBalanceException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error processing currency exchange", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to process currency exchange: " + e.getMessage()));
        }
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse> getExchangeHistory(@CurrentUser User user) {
        // Add null check for user
        if (user == null) {
            log.error("User is null in getExchangeHistory controller method");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Authentication required"));
        }

        try {
            List<ExchangeResultDto> history = currencyExchangeService.getRecentExchanges(user);
            return ResponseEntity.ok(ApiResponse.success("Exchange history retrieved successfully", history));
        } catch (Exception e) {
            log.error("Error retrieving exchange history", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve exchange history"));
        }
    }
}
