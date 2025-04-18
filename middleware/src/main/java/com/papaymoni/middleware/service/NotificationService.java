package com.papaymoni.middleware.service;

import com.papaymoni.middleware.model.Notification;
import com.papaymoni.middleware.model.User;

import java.util.List;

public interface NotificationService {
    Notification createNotification(User user, String type, String title, String message);
    List<Notification> getUserNotifications(User user);
    List<Notification> getUserUnreadNotifications(User user);
    void markNotificationAsRead(Long id);
    long countUnreadNotifications(User user);
    void sendEmail(String to, String subject, String body);
    void sendSms(String phoneNumber, String message);
}
