package com.papaymoni.middleware.event.listener;

import com.papaymoni.middleware.event.NotificationEvent;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.service.NotificationService;
import com.papaymoni.middleware.service.UserService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static com.papaymoni.middleware.config.RabbitMQConfig.NOTIFICATION_QUEUE;

@Component
public class NotificationEventListener {

    private final NotificationService notificationService;
    private final UserService userService;

    public NotificationEventListener(NotificationService notificationService,
                                     UserService userService) {
        this.notificationService = notificationService;
        this.userService = userService;
    }

    @RabbitListener(queues = NOTIFICATION_QUEUE)
    public void handleNotificationEvent(NotificationEvent event) {
        User user = userService.getUserById(event.getUserId());

        notificationService.createNotification(
                user,
                event.getType(),
                event.getTitle(),
                event.getMessage()
        );
    }
}
