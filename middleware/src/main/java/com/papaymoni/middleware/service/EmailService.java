package com.papaymoni.middleware.service;

public interface EmailService {
    /**
     * Send a simple email message
     * @param to recipient email address
     * @param subject email subject
     * @param text email body text
     * @return true if sent successfully
     */
    boolean sendSimpleMessage(String to, String subject, String text);

    /**
     * Send an email with HTML content
     * @param to recipient email address
     * @param subject email subject
     * @param htmlContent email body in HTML format
     * @return true if sent successfully
     */
    boolean sendHtmlMessage(String to, String subject, String htmlContent);
}