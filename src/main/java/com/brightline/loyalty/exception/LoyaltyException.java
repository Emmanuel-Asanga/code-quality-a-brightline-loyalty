package com.brightline.loyalty.exception;

/**
 * Base type for the failures the loyalty service reports on purpose.
 *
 * <p>It lets a boundary such as {@code Demo} handle every deliberate loyalty failure with one
 * narrow catch, without ever catching {@code Exception} and hiding programming errors.
 */
public abstract class LoyaltyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    protected LoyaltyException(String message) {
        super(message);
    }

    protected LoyaltyException(String message, Throwable cause) {
        super(message, cause);
    }
}
