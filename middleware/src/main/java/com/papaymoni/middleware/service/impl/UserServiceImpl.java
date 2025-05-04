package com.papaymoni.middleware.service.impl;
import com.papaymoni.middleware.dto.UserEventDto;
import com.papaymoni.middleware.dto.UserProfileDto;
import com.papaymoni.middleware.dto.UserRegistrationDto;
import com.papaymoni.middleware.exception.ResourceNotFoundException;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.model.VirtualAccount;
import com.papaymoni.middleware.repository.UserRepository;
import com.papaymoni.middleware.service.*;
import com.papaymoni.middleware.util.ReferralCodeGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;
    private final VirtualAccountService virtualAccountService;
    private final WalletBalanceService walletBalanceService;

    private final EncryptionService encryptionService;
    private final CacheManager cacheManager;
    private final RabbitTemplate rabbitTemplate;

    public UserServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           EncryptionService encryptionService,
                           VirtualAccountService virtualAccountService,
                           CacheManager cacheManager,
                           RabbitTemplate rabbitTemplate,
                           WalletBalanceService walletBalanceService,
                           EmailVerificationService emailVerificationService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailVerificationService = emailVerificationService;
        this.virtualAccountService = virtualAccountService;
        this.encryptionService = encryptionService;
        this.cacheManager = cacheManager;
        this.walletBalanceService = walletBalanceService;
        this.rabbitTemplate = rabbitTemplate;

    }
    @Override
    public UserEventDto getUserEventDtoById(Long id) {
        User user = getUserById(id); // reuse existing method to fetch user
        UserEventDto dto = new UserEventDto();
        dto.setUserId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setPhoneNumber(user.getPhoneNumber());
        dto.setReferredBy(user.getReferredBy());
        return dto;
    }

    @Override
    @Transactional
    public User registerUser(UserRegistrationDto registrationDto) {
        log.info("Registering new user with username: {}", registrationDto.getUsername());

        if (userRepository.existsByUsername(registrationDto.getUsername())) {
            throw new IllegalArgumentException("Username already exists");
        }

        if (userRepository.existsByEmail(registrationDto.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }

        if (!emailVerificationService.isEmailVerified(registrationDto.getEmail())) {
            throw new IllegalArgumentException("Email must be verified before registration");
        }

        User user = buildBasicUser(registrationDto);
        User savedUser = userRepository.save(user);

        // Initialize wallet balances for the new user
        walletBalanceService.initializeUserWallets(savedUser);

        return savedUser;
    }

    @Override
    @Transactional
    public User registerEnhancedUser(UserRegistrationDto registrationDto) {
        log.info("Registering enhanced user with username: {}", registrationDto.getUsername());

        if (userRepository.existsByUsername(registrationDto.getUsername())) {
            throw new IllegalArgumentException("Username already exists");
        }

        if (userRepository.existsByEmail(registrationDto.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }

        if (registrationDto.getBvn() != null && userRepository.existsByBvn(registrationDto.getBvn())) {
            throw new IllegalArgumentException("BVN already exists in the system");
        }

        if (!emailVerificationService.isEmailVerified(registrationDto.getEmail())) {
            throw new IllegalArgumentException("Email must be verified before registration");
        }

        User user = buildBasicUser(registrationDto);
        user.setBvn(registrationDto.getBvn());
        user.setDateOfBirth(registrationDto.getDateOfBirth());
        user.setGender(registrationDto.getGender());
        user.setBvnVerified(false);

        return userRepository.save(user);
    }

    private User buildBasicUser(UserRegistrationDto dto) {
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setEmail(dto.getEmail());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setPhoneNumber(dto.getPhoneNumber());
        user.setEmailVerified(true);
        user.setPhoneVerified(false);
        user.setReferralCode(ReferralCodeGenerator.generateReferralCode());

        if (dto.getReferralCode() != null && !dto.getReferralCode().isEmpty()) {
            userRepository.findByReferralCode(dto.getReferralCode())
                    .ifPresent(referrer -> user.setReferredBy(referrer.getUsername()));
        }
        return user;
    }


    @Override
    @Cacheable(value = "users", key = "#id")
    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    @Override
    @Cacheable(value = "users", key = "#username")
    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + username));
    }

    @Override
    @Cacheable(value = "users", key = "#email")
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }

    @Override
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Override
    @Transactional
    @CacheEvict(value = {"users", "userProfiles"}, allEntries = true)
    public User updateUser(User user) {
        if (!userRepository.existsById(user.getId())) {
            throw new ResourceNotFoundException("User not found with id: " + user.getId());
        }
        return userRepository.save(user);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"users", "userProfiles"}, allEntries = true)
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("User not found with id: " + id);
        }
        userRepository.deleteById(id);
    }

    @Override
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    public boolean existsByBvn(String bvn) {
        return userRepository.existsByBvn(bvn);
    }

    @Override
    @Cacheable(value = "users", key = "#usernameOrEmail")
    public User getUserByUsernameOrEmail(String usernameOrEmail) {
        return userRepository.findByUsernameOrEmail(usernameOrEmail, usernameOrEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username or email: " + usernameOrEmail));
    }

    @Override
    @Transactional
    @CacheEvict(value = {"users", "userProfiles"}, key = "#email")
    public User updateEmailVerificationStatus(String email, boolean status) {
        User user = getUserByEmail(email);
        user.setEmailVerified(status);
        return userRepository.save(user);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"users", "userProfiles"}, allEntries = true)
    public User updateBvnVerificationStatus(String bvn, boolean status) {
        User user = userRepository.findByBvn(bvn)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with BVN: " + bvn));
        user.setBvnVerified(status);
        return userRepository.save(user);
    }

    @Override
    @Cacheable(value = "userProfiles", key = "#username")
    @Transactional(readOnly = true)
    public UserProfileDto getUserProfileByUsername(String username) {
        log.debug("Fetching user profile for username: {}", username);

        // Get user without loading virtual accounts
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + username));

        // Create DTO and populate basic user info
        UserProfileDto dto = new UserProfileDto();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setPhoneNumber(user.getPhoneNumber());
        dto.setReferralCode(user.getReferralCode());

        // Use the virtual account service to get accounts
        List<VirtualAccount> accounts = virtualAccountService.getUserVirtualAccounts(user);

        // Convert accounts to DTOs without circular references
        if (accounts != null && !accounts.isEmpty()) {
            List<UserProfileDto.VirtualAccountDto> accountDtos = accounts.stream()
                    .map(account -> {
                        UserProfileDto.VirtualAccountDto accountDto = new UserProfileDto.VirtualAccountDto();
                        accountDto.setId(account.getId());
                        accountDto.setAccountNumber(account.getAccountNumber());
                        accountDto.setBankCode(account.getBankCode());
                        accountDto.setBankName(account.getBankName());
                        accountDto.setAccountName(account.getAccountName());
                        accountDto.setCurrency(account.getCurrency());
                        accountDto.setActive(account.isActive());
                        return accountDto;
                    })
                    .collect(Collectors.toList());

            dto.setVirtualAccounts(accountDtos);
        }

        return dto;
    }
//
//    private UserProfileDto convertToDto(User user) {
//        UserProfileDto dto = new UserProfileDto();
//        dto.setId(user.getId());
//        dto.setUsername(user.getUsername());
//        dto.setEmail(user.getEmail());
//        dto.setFirstName(user.getFirstName());
//        dto.setLastName(user.getLastName());
//        dto.setPhoneNumber(user.getPhoneNumber());
//        dto.setReferralCode(user.getReferralCode());
//
//        if (user.getVirtualAccounts() != null && !user.getVirtualAccounts().isEmpty()) {
//            VirtualAccount firstAccount = user.getVirtualAccounts().iterator().next();
//            dto.setVirtualAccount(firstAccount);
//        }
//
//        return dto;
//    }
}
