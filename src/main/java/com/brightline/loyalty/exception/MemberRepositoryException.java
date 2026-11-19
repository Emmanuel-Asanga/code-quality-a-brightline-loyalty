package com.brightline.loyalty.exception;

/**
 * Signals that a {@code MemberRepository} could not complete a lookup because of an
 * infrastructure failure (for example a database outage). It is not used for a member that
 * is simply absent.
 */
public class MemberRepositoryException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public MemberRepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
