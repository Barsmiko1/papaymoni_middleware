package com.papaymoni.middleware.event.listener;

import com.papaymoni.middleware.event.ReferralEvent;
import com.papaymoni.middleware.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static com.papaymoni.middleware.config.RabbitMQConfig.NOTIFICATION_QUEUE;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReferralEventListener {

    private final NotificationService notificationService;

    @RabbitListener(queues = NOTIFICATION_QUEUE)
    public void handleReferralEvent(ReferralEvent event) {
        if ("REFERRAL_BONUS".equals(event.getEventType())) {
            log.info("Processing referral bonus event for referrer: {} and referred user: {}",
                    event.getReferrerId(), event.getReferredUserId());

            // Additional processing can be added here
            // e.g., analytics, reporting, additional notifications
        }
    }
}
