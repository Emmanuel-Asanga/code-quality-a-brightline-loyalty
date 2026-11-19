package com.brightline.loyalty.exception;

/** Thrown when points are requested for a member that does not exist. */
public class MemberNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String memberId;

    public MemberNotFoundException(String memberId) {
        super("No member found with id '" + memberId + "'");
        this.memberId = memberId;
    }

    public String memberId() {
        return memberId;
    }
}
