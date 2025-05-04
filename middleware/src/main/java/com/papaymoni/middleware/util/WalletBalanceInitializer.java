package com.papaymoni.middleware.util;

import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.repository.UserRepository;
import com.papaymoni.middleware.service.WalletBalanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class WalletBalanceInitializer {

    private final UserRepository userRepository;
    private final WalletBalanceService walletBalanceService;

    @Bean
    CommandLineRunner initializeWalletBalances() {
        return args -> {
            log.info("Checking for users without wallet balances...");
            List<User> users = userRepository.findAll();

            for (User user : users) {
                if (walletBalanceService.getUserWalletBalances(user).isEmpty()) {
                    walletBalanceService.initializeUserWallets(user);
                    log.info("Initialized wallet balances for user: {}", user.getId());
                }
            }

            log.info("Wallet balance initialization complete.");
        };
    }
}