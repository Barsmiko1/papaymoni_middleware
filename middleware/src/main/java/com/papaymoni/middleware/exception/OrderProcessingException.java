package com.papaymoni.middleware.exception;

/**
 * Exception thrown when there is an error processing an order
 */
public class OrderProcessingException extends RuntimeException {
    public OrderProcessingException(String message) {
        super(message);
    }

    public OrderProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
