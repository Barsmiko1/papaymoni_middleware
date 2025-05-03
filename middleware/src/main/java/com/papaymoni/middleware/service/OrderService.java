package com.papaymoni.middleware.service;

import com.papaymoni.middleware.dto.OrderDto;
import com.papaymoni.middleware.model.Order;
import com.papaymoni.middleware.model.User;

import java.util.List;

public interface OrderService {
    Order createOrder(User user, OrderDto orderDto);
    Order getOrderById(String orderId);
    List<Order> getUserOrders(User user);
    List<Order> getPendingOrders(User user);
    List<Order> getPendingBuyOrders();
    List<Order> getPendingSellOrders();
    void processOrder(Order order);
    void markOrderAsPaid(String orderId, String paymentType, String paymentId);
    void releaseAssets(String orderId);
    void sendOrderMessage(String orderId, String message);
    void uploadOrderReceipt(String orderId, byte[] receiptData, String filename);
    void cancelOrder(String orderId);

    /**
     * Get total count of orders in the system
     * @return Count of orders
     */
    long getOrderCount();
}