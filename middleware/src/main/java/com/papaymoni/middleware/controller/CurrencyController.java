package com.papaymoni.middleware.controller;

import com.papaymoni.middleware.dto.ApiResponse;
import com.papaymoni.middleware.dto.CurrencyDto;
import com.papaymoni.middleware.security.CurrentUser;
import com.papaymoni.middleware.service.CurrencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.security.Principal;

@Slf4j
@RestController
@RequestMapping("/api/currencies")
@RequiredArgsConstructor
public class CurrencyController {

    private final CurrencyService currencyService;

    @GetMapping
    public ResponseEntity<ApiResponse> getAllCurrencies() {
        return ResponseEntity.ok(ApiResponse.success("All currencies retrieved successfully",
                currencyService.getAllCurrencies()));
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse> getActiveCurrencies() {
        return ResponseEntity.ok(ApiResponse.success("Active currencies retrieved successfully",
                currencyService.getActiveCurrencies()));
    }

    @GetMapping("/{code}")
    public ResponseEntity<ApiResponse> getCurrencyByCode(@PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.success("Currency retrieved successfully",
                currencyService.getCurrencyByCode(code)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse> createCurrency(@Valid @RequestBody CurrencyDto currencyDto, Principal principal) {
        return ResponseEntity.ok(ApiResponse.success("Currency created successfully",
                currencyService.createCurrency(currencyDto, principal.getName())));
    }

    @PutMapping("/{code}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse> updateCurrency(@PathVariable String code,
                                                      @Valid @RequestBody CurrencyDto currencyDto,
                                                      Principal principal) {
        return ResponseEntity.ok(ApiResponse.success("Currency updated successfully",
                currencyService.updateCurrency(code, currencyDto, principal.getName())));
    }

    @PatchMapping("/{code}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse> toggleCurrencyStatus(@PathVariable String code,
                                                            @RequestParam boolean active,
                                                            Principal principal) {
        currencyService.toggleCurrencyStatus(code, active, principal.getName());
        return ResponseEntity.ok(ApiResponse.success("Currency status updated successfully"));
    }
}
