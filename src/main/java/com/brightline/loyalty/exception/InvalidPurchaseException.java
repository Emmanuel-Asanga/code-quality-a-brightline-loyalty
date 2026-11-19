package com.brightline.loyalty.exception;

/** Thrown when a purchase amount cannot be turned into loyalty points. */
public class InvalidPurchaseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidPurchaseException(String message) {
        super(message);
    }
}
