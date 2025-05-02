//package com.papaymoni.middleware.event.listener;
//
//import com.papaymoni.middleware.event.NotificationEvent;
//import com.papaymoni.middleware.model.User;
//import com.papaymoni.middleware.service.NotificationService;
//import com.papaymoni.middleware.service.UserService;
//import org.springframework.amqp.rabbit.annotation.RabbitListener;
//import org.springframework.stereotype.Component;
//
//import static com.papaymoni.middleware.config.RabbitMQConfig.NOTIFICATION_QUEUE;
//
//@Component
//public class NotificationEventListener {
//
//    private final NotificationService notificationService;
//    private final UserService userService;
//
//    public NotificationEventListener(NotificationService notificationService,
//                                     UserService userService) {
//        this.notificationService = notificationService;
//        this.userService = userService;
//    }
//
//    @RabbitListener(queues = NOTIFICATION_QUEUE)
//    public void handleNotificationEvent(NotificationEvent event) {
//        User user = userService.getUserById(event.getUserId());
//
//        notificationService.createNotification(
//                user,
//                event.getType(),
//                event.getTitle(),
//                event.getMessage()
//        );
//    }
//}

package com.papaymoni.middleware.event.listener;

import com.papaymoni.middleware.event.NotificationEvent;
import com.papaymoni.middleware.service.NotificationService;
import com.papaymoni.middleware.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static com.papaymoni.middleware.config.RabbitMQConfig.NOTIFICATION_QUEUE;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;
    private final UserService userService;

    @RabbitListener(queues = NOTIFICATION_QUEUE)
    public void handleNotificationEvent(NotificationEvent event) {
        log.info("Handling notification event for user: {}", event.getUserId());

        try {
            // Don't load the full User entity, just use the ID
            notificationService.createNotificationForUser(
                    event.getUserId(),
                    event.getType(),
                    event.getTitle(),
                    event.getMessage()
            );

            log.info("Successfully processed notification event for user: {}", event.getUserId());
        } catch (Exception e) {
            log.error("Error handling notification event: {}", e.getMessage(), e);
        }
    }
}
