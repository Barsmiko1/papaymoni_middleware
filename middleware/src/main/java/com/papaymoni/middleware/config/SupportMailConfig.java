package com.papaymoni.middleware.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
public class SupportMailConfig {

    @Value("${spring.support.mail.host}")
    private String mailHost;

    @Value("${spring.support.mail.port}")
    private int mailPort;

    @Value("${spring.support.mail.username}")
    private String mailUsername;

    @Value("${spring.support.mail.password}")
    private String mailPassword;

    @Value("${spring.support.mail.properties.mail.smtp.auth}")
    private String mailSmtpAuth;

    @Value("${spring.support.mail.properties.mail.smtp.starttls}")
    private String mailSmtpStartTls;

    @Value("${spring.support.mail.properties.mail.smtp.connectiontimeout}")
    private int connectionTimeout;

    @Value("${spring.support.mail.properties.mail.smtp.timeout}")
    private int timeout;

    @Value("${spring.support.mail.properties.mail.smtp.writetimeout}")
    private int writeTimeout;

    @Bean
    @Qualifier("supportMailSender")
    public JavaMailSender supportMailSender() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();

        // Server settings from application.properties
        mailSender.setHost(mailHost);
        mailSender.setPort(mailPort);
        mailSender.setUsername(mailUsername);
        mailSender.setPassword(mailPassword);

        // Properties
        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", mailSmtpAuth);
        props.put("mail.smtp.starttls.enable", mailSmtpStartTls);
        props.put("mail.debug", "false");
        props.put("mail.smtp.connectiontimeout", connectionTimeout);
        props.put("mail.smtp.timeout", timeout);
        props.put("mail.smtp.writetimeout", writeTimeout);

        return mailSender;
    }
}
