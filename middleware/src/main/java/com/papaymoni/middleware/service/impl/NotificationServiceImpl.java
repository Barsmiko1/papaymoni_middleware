package com.papaymoni.middleware.service.impl;

import com.papaymoni.middleware.exception.ResourceNotFoundException;
import com.papaymoni.middleware.model.Notification;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.repository.NotificationRepository;
import com.papaymoni.middleware.service.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationServiceImpl(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    @Transactional
    public Notification createNotification(User user, String type, String title, String message) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setRead(false);
        notification.setStatus("PENDING");

        Notification savedNotification = notificationRepository.save(notification);

        // Send the notification based on type
        if ("EMAIL".equals(type)) {
            sendEmail(user.getEmail(), title, message);
            savedNotification.setStatus("SENT");
            savedNotification.setSentAt(LocalDateTime.now());
        } else if ("SMS".equals(type)) {
            sendSms(user.getPhoneNumber(), message);
            savedNotification.setStatus("SENT");
            savedNotification.setSentAt(LocalDateTime.now());
        }

        return notificationRepository.save(savedNotification);
    }

    @Override
    public List<Notification> getUserNotifications(User user) {
        return notificationRepository.findByUser(user);
    }

    @Override
    public List<Notification> getUserUnreadNotifications(User user) {
        return notificationRepository.findByUserAndIsRead(user, false);
    }

    @Override
    @Transactional
    public void markNotificationAsRead(Long id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + id));

        notification.setRead(true);
        notification.setReadAt(LocalDateTime.now());
        notificationRepository.save(notification);
    }

    @Override
    public long countUnreadNotifications(User user) {
        return notificationRepository.countByUserAndIsRead(user, false);
    }

    @Override
    public void sendEmail(String to, String subject, String body) {
        // this would send an email
        System.out.println("Sending email to: " + to);
        System.out.println("Subject: " + subject);
        System.out.println("Body: " + body);
    }

    @Override
    public void sendSms(String phoneNumber, String message) {
        // this would send an SMS
        // For now, we'll just log it
        System.out.println("Sending SMS to: " + phoneNumber);
        System.out.println("Message: " + message);
    }
}
