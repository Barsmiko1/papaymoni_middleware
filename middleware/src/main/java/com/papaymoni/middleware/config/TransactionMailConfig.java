package com.papaymoni.middleware.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
public class TransactionMailConfig {

    @Value("${spring.mail.transactions.host}")
    private String mailHost;

    @Value("${spring.mail.transactions.port}")
    private int mailPort;

    @Value("${spring.mail.transactions.username}")
    private String mailUsername;

    @Value("${spring.mail.transactions.password}")
    private String mailPassword;

    @Value("${spring.mail.transactions.from.address:alert@papaymoni.com}")
    private String fromAddress;

    @Bean(name = "transactionMailSender")
    public JavaMailSender transactionMailSender() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();

        // Server settings for transaction emails
        mailSender.setHost(mailHost);
        mailSender.setPort(mailPort);
        mailSender.setUsername(mailUsername);
        mailSender.setPassword(mailPassword);

        // Properties
        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.from", fromAddress);
        props.put("mail.debug", "true");

        return mailSender;
    }
}