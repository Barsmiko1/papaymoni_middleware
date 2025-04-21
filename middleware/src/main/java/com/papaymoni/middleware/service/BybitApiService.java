package com.papaymoni.middleware.service;

import com.papaymoni.middleware.dto.BybitApiResponse;
import com.papaymoni.middleware.model.BybitCredentials;

public interface BybitApiService {
    /**
     * Test basic connectivity to Bybit API
     * @return true if connection is successful
     */
    boolean testConnection();

    <T> BybitApiResponse<T> executeRequest(String endpoint, String method, Object payload, BybitCredentials credentials, Class<T> responseType);
    boolean verifyCredentials(BybitCredentials credentials);
    BybitApiResponse<?> getAds(String tokenId, String currencyId, String side, BybitCredentials credentials);
    BybitApiResponse<?> createAd(Object adPayload, BybitCredentials credentials);
    BybitApiResponse<?> cancelAd(String itemId, BybitCredentials credentials);
    BybitApiResponse<?> updateAd(Object updatePayload, BybitCredentials credentials);
    BybitApiResponse<?> getPersonalAds(BybitCredentials credentials);
    BybitApiResponse<?> getOrders(Object filterPayload, BybitCredentials credentials);
    BybitApiResponse<?> getOrderDetail(String orderId, BybitCredentials credentials);
    BybitApiResponse<?> getPendingOrders(Object filterPayload, BybitCredentials credentials);
    BybitApiResponse<?> markOrderAsPaid(String orderId, String paymentType, String paymentId, BybitCredentials credentials);
    BybitApiResponse<?> releaseAssets(String orderId, BybitCredentials credentials);
    BybitApiResponse<?> sendChatMessage(String message, String contentType, String orderId, BybitCredentials credentials);
    BybitApiResponse<?> uploadChatFile(byte[] fileData, String filename, String orderId, BybitCredentials credentials);
}
