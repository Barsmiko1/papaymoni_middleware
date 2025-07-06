package com.papaymoni.middleware.service;

/**
 * Service for sending support-related emails
 */
public interface SupportEmailService {

    /**
     * Send a simple text email
     *
     * @param to recipient email address
     * @param subject email subject
     * @param text email body text
     * @return true if email was sent successfully, false otherwise
     */
    boolean sendSimpleMessage(String to, String subject, String text);

    /**
     * Send an HTML email
     *
     * @param to recipient email address
     * @param subject email subject
     * @param htmlContent email body as HTML
     * @return true if email was sent successfully, false otherwise
     */
    boolean sendHtmlMessage(String to, String subject, String htmlContent);

    /**
     * Send an HTML email with CC recipients
     *
     * @param to recipient email address
     * @param cc cc recipient email addresses
     * @param subject email subject
     * @param htmlContent email body as HTML
     * @return true if email was sent successfully, false otherwise
     */
    boolean sendHtmlMessageWithCc(String to, String[] cc, String subject, String htmlContent);

    /**
     * Send an HTML email with attachment
     *
     * @param to recipient email address
     * @param subject email subject
     * @param htmlContent email body as HTML
     * @param attachmentFilename name of the attachment
     * @param attachmentData attachment data as byte array
     * @param mimeType MIME type of the attachment
     * @return true if email was sent successfully, false otherwise
     */
    boolean sendHtmlMessageWithAttachment(String to, String subject, String htmlContent,
                                          String attachmentFilename, byte[] attachmentData, String mimeType);
}
