//package com.papaymoni.middleware.service.impl;
//
//import com.papaymoni.middleware.dto.TransactionCacheDto;
//import com.papaymoni.middleware.exception.ResourceNotFoundException;
//import com.papaymoni.middleware.model.Transaction;
//import com.papaymoni.middleware.model.User;
//import com.papaymoni.middleware.repository.TransactionRepository;
//import com.papaymoni.middleware.service.TransactionService;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.BeanUtils;
//import org.springframework.cache.annotation.Cacheable;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import javax.persistence.EntityManager;
//import javax.persistence.PersistenceContext;
//import java.math.BigDecimal;
//import java.time.LocalDateTime;
//import java.util.List;
//import java.util.stream.Collectors;
//
//@Slf4j
//@Service
//public class TransactionServiceImpl implements TransactionService {
//
//    private final TransactionRepository transactionRepository;
//
//    @PersistenceContext
//    private EntityManager entityManager;
//
//    public TransactionServiceImpl(TransactionRepository transactionRepository) {
//        this.transactionRepository = transactionRepository;
//    }
//
//    @Override
//    // @Cacheable(value = "transactionById", key = "#id")
//    @Transactional(readOnly = true)
//    public Transaction getTransactionById(Long id) {
//        return transactionRepository.findByIdWithFullFetch(id)
//                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with id: " + id));
//    }
//
//    // Create a method specifically for caching purposes
//    @Cacheable(value = "transactionDtoById", key = "#id")
//    @Transactional(readOnly = true)
//    public TransactionCacheDto getTransactionDtoById(Long id) {
//        Transaction transaction = transactionRepository.findByIdWithFullFetch(id)
//                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with id: " + id));
//
//        return convertToDto(transaction);
//    }
//    private TransactionCacheDto convertToDto(Transaction transaction) {
//        TransactionCacheDto dto = new TransactionCacheDto();
//        BeanUtils.copyProperties(transaction, dto);
//
//        // Manually copy related entity IDs and necessary fields
//        if (transaction.getUser() != null) {
//            dto.setUserId(transaction.getUser().getId());
//            dto.setUserName(transaction.getUser().getUsername());
//            dto.setUserFirstName(transaction.getUser().getFirstName());
//            dto.setUserEmail(transaction.getUser().getEmail());
//        }
//
//        if (transaction.getOrder() != null) {
//            dto.setOrderId(transaction.getOrder().getId() != null ? Long.parseLong(transaction.getOrder().getId()) : null);
//        }
//
//        if (transaction.getVirtualAccount() != null) {
//            dto.setVirtualAccountId(transaction.getVirtualAccount().getId());
//            dto.setVirtualAccountNumber(transaction.getVirtualAccount().getAccountNumber());
//        }
//
//        return dto;
//    }
//
//    @Override
//    //  @Cacheable(value = "transactionsByUser", key = "#user.id")
//    @Transactional(readOnly = true)
//    public List<Transaction> getUserTransactions(User user) {
//        List<Transaction> transactions = transactionRepository.findByUserWithFullFetch(user.getId());
//        log.info("Transactions: {}", transactions.size());
//        log.info("Transactions: {}", transactions);
//
//        return transactions;
//    }
//
//    @Override
//    //@Cacheable(value = "transactionsByUserAndType", key = "#user.id + '-' + #type")
//    @Transactional(readOnly = true)
//    public List<Transaction> getUserTransactionsByType(User user, String type) {
//        // First get all transactions with proper fetching to avoid N+1 queries
//        List<Transaction> allTransactions = transactionRepository.findByUserWithFullFetch(user.getId());
//
//        // Then filter by type in memory
//        return allTransactions.stream()
//                .filter(t -> type.equals(t.getTransactionType()))
//                .collect(Collectors.toList());
//    }
//
//    @Override
//    @Transactional(readOnly = true)
//    public List<Transaction> getUserTransactionsByDateRange(User user, LocalDateTime start, LocalDateTime end) {
//        return transactionRepository.findByUserAndCreatedAtBetweenWithFetch(user.getId(), start, end);
//    }
//
//    @Override
//    @Transactional(readOnly = true)
//    public Transaction findMatchingDeposit(User user, BigDecimal amount, String reference) {
//        // Find a deposit transaction that matches the amount and has a reference to the target user
//        List<Transaction> transactions = transactionRepository.findByUserAndAmountAndStatus(user, amount, "COMPLETED");
//
//        // Filter by reference (could be in payment details or external reference)
//        return transactions.stream()
//                .filter(t -> "DEPOSIT".equals(t.getTransactionType()) &&
//                        ((t.getPaymentDetails() != null && t.getPaymentDetails().contains(reference)) ||
//                                (t.getExternalReference() != null && t.getExternalReference().contains(reference))))
//                .findFirst()
//                .orElse(null);
//    }
//
//    @Override
//    //@Cacheable(value = "recentTransactions", key = "#user.id + '-' + #limit")
//    @Transactional(readOnly = true)
//    public List<Transaction> getRecentTransactions(User user, int limit) {
//        // Get all transactions and then limit them
//        List<Transaction> allTransactions = transactionRepository.findByUserWithFullFetch(user.getId());
//
//        // Sort by createdAt in descending order and limit
//        return allTransactions.stream()
//                .sorted((t1, t2) -> t2.getCreatedAt().compareTo(t1.getCreatedAt()))
//                .limit(limit)
//                .collect(Collectors.toList());
//    }
//
//    @Override
//    @Transactional(readOnly = true)
//    public boolean existsByExternalReference(String externalReference) {
//        return transactionRepository.existsByExternalReference(externalReference);
//    }
//
//    @Override
//    @Transactional
//    public Transaction save(Transaction transaction) {
//        return transactionRepository.save(transaction);
//    }
//
//    @Override
//    public void updateTransaction(Transaction transaction) {
//
//    }
//}
//
//
//
//

package com.papaymoni.middleware.service.impl;

import com.papaymoni.middleware.dto.TransactionCacheDto;
import com.papaymoni.middleware.exception.ResourceNotFoundException;
import com.papaymoni.middleware.model.Transaction;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.repository.TransactionRepository;
import com.papaymoni.middleware.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    // @Cacheable(value = "transactionById", key = "#id")
    @Transactional(readOnly = true)
    public Transaction getTransactionById(Long id) {
        return transactionRepository.findByIdWithFullFetch(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with id: " + id));
    }

    // Create a method specifically for caching purposes
    @Cacheable(value = "transactionDtoById", key = "#id")
    @Transactional(readOnly = true)
    public TransactionCacheDto getTransactionDtoById(Long id) {
        Transaction transaction = transactionRepository.findByIdWithFullFetch(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with id: " + id));

        return convertToDto(transaction);
    }
    private TransactionCacheDto convertToDto(Transaction transaction) {
        TransactionCacheDto dto = new TransactionCacheDto();
        BeanUtils.copyProperties(transaction, dto);

        // Manually copy related entity IDs and necessary fields
        if (transaction.getUser() != null) {
            dto.setUserId(transaction.getUser().getId());
            dto.setUserName(transaction.getUser().getUsername());
            dto.setUserFirstName(transaction.getUser().getFirstName());
            dto.setUserEmail(transaction.getUser().getEmail());
        }

        if (transaction.getOrder() != null) {
            dto.setOrderId(transaction.getOrder().getId() != null ? Long.parseLong(transaction.getOrder().getId()) : null);
        }

        if (transaction.getVirtualAccount() != null) {
            dto.setVirtualAccountId(transaction.getVirtualAccount().getId());
            dto.setVirtualAccountNumber(transaction.getVirtualAccount().getAccountNumber());
        }

        return dto;
    }

    @Override
    //  @Cacheable(value = "transactionsByUser", key = "#user.id")
    @Transactional(readOnly = true)
    public List<Transaction> getUserTransactions(User user) {
        List<Transaction> transactions = transactionRepository.findByUserWithFullFetch(user.getId());
        log.info("Transactions: {}", transactions.size());
        log.info("Transactions: {}", transactions);

        return transactions;
    }

    @Override
    //@Cacheable(value = "transactionsByUserAndType", key = "#user.id + '-' + #type")
    @Transactional(readOnly = true)
    public List<Transaction> getUserTransactionsByType(User user, String type) {
        // First get all transactions with proper fetching to avoid N+1 queries
        List<Transaction> allTransactions = transactionRepository.findByUserWithFullFetch(user.getId());

        // Then filter by type in memory
        return allTransactions.stream()
                .filter(t -> type.equals(t.getTransactionType()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Transaction> getUserTransactionsByDateRange(User user, LocalDateTime start, LocalDateTime end) {
        return transactionRepository.findByUserAndCreatedAtBetweenWithFetch(user.getId(), start, end);
    }

    @Override
    @Transactional(readOnly = true)
    public Transaction findMatchingDeposit(User user, BigDecimal amount, String reference) {
        // Find a deposit transaction that matches the amount and has a reference to the target user
        List<Transaction> transactions = transactionRepository.findByUserAndAmountAndStatus(user, amount, "COMPLETED");

        // Filter by reference (could be in payment details or external reference)
        return transactions.stream()
                .filter(t -> "DEPOSIT".equals(t.getTransactionType()) &&
                        ((t.getPaymentDetails() != null && t.getPaymentDetails().contains(reference)) ||
                                (t.getExternalReference() != null && t.getExternalReference().contains(reference))))
                .findFirst()
                .orElse(null);
    }

    @Override
    //@Cacheable(value = "recentTransactions", key = "#user.id + '-' + #limit")
    @Transactional(readOnly = true)
    public List<Transaction> getRecentTransactions(User user, int limit) {
        // Get all transactions and then limit them
        List<Transaction> allTransactions = transactionRepository.findByUserWithFullFetch(user.getId());

        // Sort by createdAt in descending order and limit
        return allTransactions.stream()
                .sorted((t1, t2) -> t2.getCreatedAt().compareTo(t1.getCreatedAt()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByExternalReference(String externalReference) {
        return transactionRepository.existsByExternalReference(externalReference);
    }

    @Override
    @Transactional
    public Transaction save(Transaction transaction) {
        return transactionRepository.save(transaction);
    }

    @Override
    public Transaction findById(Long id) {
        return transactionRepository.findById(id).orElse(null);
    }

    @Override
    public List<Transaction> findByUser(User user) {
        return transactionRepository.findByUser(user);
    }

    @Override
    public List<Transaction> findByUserOrderByCreatedAtDesc(User user) {
        return transactionRepository.findByUserOrderByCreatedAtDesc(user);
    }
}

