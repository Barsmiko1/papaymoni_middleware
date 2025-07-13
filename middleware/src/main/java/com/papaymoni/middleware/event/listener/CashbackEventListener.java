package com.papaymoni.middleware.event.listener;

import com.papaymoni.middleware.event.CashbackEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static com.papaymoni.middleware.config.RabbitMQConfig.NOTIFICATION_QUEUE;

@Slf4j
@Component
@RequiredArgsConstructor
public class CashbackEventListener {

    @RabbitListener(queues = NOTIFICATION_QUEUE)
    public void handleCashbackEvent(CashbackEvent event) {
        log.info("Processing cashback event for user: {} amount: {} {}",
                event.getUserId(), event.getCashbackAmount(), event.getCurrency());

        // Additional processing for cashback analytics
        // Can be used for reporting, trend analysis, etc.
    }
}
