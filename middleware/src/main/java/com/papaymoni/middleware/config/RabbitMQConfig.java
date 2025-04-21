package com.papaymoni.middleware.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.support.RetryTemplate;

/**
 * RabbitMQ configuration for event-driven architecture
 */
@Configuration
@EnableRabbit
public class RabbitMQConfig {

    // Queue names
    public static final String ORDER_QUEUE = "order-queue";
    public static final String PAYMENT_QUEUE = "payment-queue";
    public static final String NOTIFICATION_QUEUE = "notification-queue";
    public static final String DEAD_LETTER_QUEUE = "dead-letter-queue";

    // Exchange names
    public static final String ORDER_EXCHANGE = "order-exchange";
    public static final String PAYMENT_EXCHANGE = "payment-exchange";
    public static final String NOTIFICATION_EXCHANGE = "notification-exchange";
    public static final String DEAD_LETTER_EXCHANGE = "dead-letter-exchange";

    // Routing keys
    public static final String ORDER_CREATED_KEY = "order.created";
    public static final String ORDER_UPDATED_KEY = "order.updated";
    public static final String ORDER_COMPLETED_KEY = "order.completed";
    public static final String PAYMENT_PROCESSED_KEY = "payment.processed";
    public static final String NOTIFICATION_KEY = "notification.send";
    public static final String DEAD_LETTER_KEY = "dead-letter";

    // Dead letter configuration
    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE);
    }

    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue())
                .to(deadLetterExchange())
                .with(DEAD_LETTER_KEY);
    }

    // Queues with dead letter configuration
    @Bean
    public Queue orderQueue() {
        return QueueBuilder.durable(ORDER_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DEAD_LETTER_KEY)
                .build();
    }

    @Bean
    public Queue paymentQueue() {
        return QueueBuilder.durable(PAYMENT_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DEAD_LETTER_KEY)
                .build();
    }

    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DEAD_LETTER_KEY)
                .build();
    }

    // Exchanges
    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange(ORDER_EXCHANGE);
    }

    @Bean
    public TopicExchange paymentExchange() {
        return new TopicExchange(PAYMENT_EXCHANGE);
    }

    @Bean
    public TopicExchange notificationExchange() {
        return new TopicExchange(NOTIFICATION_EXCHANGE);
    }

    // Bindings
    @Bean
    public Binding orderBinding() {
        return BindingBuilder.bind(orderQueue()).to(orderExchange()).with("order.*");
    }

    @Bean
    public Binding paymentBinding() {
        return BindingBuilder.bind(paymentQueue()).to(paymentExchange()).with("payment.*");
    }

    @Bean
    public Binding notificationBinding() {
        return BindingBuilder.bind(notificationQueue()).to(notificationExchange()).with("notification.*");
    }

    // Message converter with Java 8 date/time support
    @Bean
    public MessageConverter jsonMessageConverter() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    // RabbitTemplate with retry capability
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());

        // Configure retry
        RetryTemplate retryTemplate = new RetryTemplate();
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(500);
        backOffPolicy.setMultiplier(2.0);
        backOffPolicy.setMaxInterval(10000);
        retryTemplate.setBackOffPolicy(backOffPolicy);

        template.setRetryTemplate(retryTemplate);

        return template;
    }
}
