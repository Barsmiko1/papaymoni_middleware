package com.papaymoni.middleware.repository;

import com.papaymoni.middleware.model.Notification;
import com.papaymoni.middleware.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUser(User user);
    List<Notification> findByUserAndRead(User user, boolean read);
    List<Notification> findByUserAndType(User user, String type);
    long countByUserAndRead(User user, boolean read);
}
