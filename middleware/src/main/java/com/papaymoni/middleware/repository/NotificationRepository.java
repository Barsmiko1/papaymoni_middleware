package com.papaymoni.middleware.repository;

import com.papaymoni.middleware.model.Notification;
import com.papaymoni.middleware.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    // Changed from 'read' to 'isRead' to match the entity field
    List<Notification> findByUser(User user);
    List<Notification> findByUserAndIsRead(User user, boolean isRead);
    List<Notification> findByUserAndType(User user, String type);
    long countByUserAndIsRead(User user, boolean isRead);
}
