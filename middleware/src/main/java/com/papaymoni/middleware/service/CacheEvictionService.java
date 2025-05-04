package com.papaymoni.middleware.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CacheEvictionService {

    private final CacheManager cacheManager;

    public CacheEvictionService(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void evictUserTransactionCaches(Long userId) {
        Cache transactionsCache = cacheManager.getCache("transactionsByUser");
        if (transactionsCache != null) {
            transactionsCache.evict(userId);
        }
    }

    public void evictVirtualAccountCaches(Long userId) {
        Cache virtualAccountsCache = cacheManager.getCache("virtualAccountsByUser");
        if (virtualAccountsCache != null) {
            virtualAccountsCache.evict(userId);
        }
    }

    public void evictTransactionCache(Long transactionId) {
        Cache transactionCache = cacheManager.getCache("transactionById");
        if (transactionCache != null) {
            transactionCache.evict(transactionId);
        }

        Cache receiptCache = cacheManager.getCache("transactionReceipts");
        if (receiptCache != null) {
            receiptCache.evict(transactionId);
        }
    }
}
