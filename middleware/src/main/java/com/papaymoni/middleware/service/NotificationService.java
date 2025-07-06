//package com.papaymoni.middleware.service;
//
//import com.papaymoni.middleware.model.Notification;
//import com.papaymoni.middleware.model.User;
//
//import java.math.BigDecimal;
//import java.util.List;
//
//public interface NotificationService {
//    Notification createNotification(User user, String type, String title, String message);
//    Notification createNotificationForUser(Long userId, String type, String title, String message);
//    List<Notification> getUserNotifications(User user);
//    List<Notification> getUserUnreadNotifications(User user);
//    void markNotificationAsRead(Long id);
//    long countUnreadNotifications(User user);
//    boolean sendEmail(String to, String subject, String body);
//    void sendSms(String phoneNumber, String message);
//
//    // Enhanced notification methods
//    boolean sendTransactionEmail(String to, String subject, String body, String transactionId);
//    boolean sendDepositNotification(User user, BigDecimal amount, String currency, Long transactionId);
//    boolean sendWithdrawalNotification(User user, BigDecimal amount, String currency, Long transactionId, BigDecimal fee);
//    boolean sendWithdrawalNotification(Long userId, String email, BigDecimal amount, String currency, Long transactionId, BigDecimal fee);
//
//    boolean sendOrderNotification(User user, String orderStatus, String orderId, BigDecimal amount, String currency);
//    boolean sendVerificationEmail(String to, String verificationCode);
//    boolean sendPasswordResetEmail(String to, String resetToken);
//}


package com.papaymoni.middleware.service;

import com.papaymoni.middleware.model.Notification;
import com.papaymoni.middleware.model.User;

import java.math.BigDecimal;
import java.util.List;

public interface NotificationService {
    Notification createNotification(User user, String type, String title, String message);
    Notification createNotificationForUser(Long userId, String type, String title, String message);
    List<Notification> getUserNotifications(User user);
    List<Notification> getUserUnreadNotifications(User user);
    void markNotificationAsRead(Long id);
    long countUnreadNotifications(User user);
    boolean sendEmail(String to, String subject, String body);
    void sendSms(String phoneNumber, String message);

    // Enhanced notification methods
    boolean sendTransactionEmail(String to, String subject, String body, String transactionId);
    boolean sendDepositNotification(User user, BigDecimal amount, String currency, Long transactionId);
    boolean sendWithdrawalNotification(Long userId, String email, BigDecimal amount, String currency, Long transactionId, BigDecimal fee);
    boolean sendWithdrawalNotification(User user, BigDecimal amount, String currency, Long transactionId, BigDecimal fee);
    boolean sendOrderNotification(User user, String orderStatus, String orderId, BigDecimal amount, String currency);
    boolean sendVerificationEmail(String to, String verificationCode);
    boolean sendPasswordResetEmail(String to, String resetToken);
}
