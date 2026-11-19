package com.brightline.loyalty.domain;

/**
 * A valid loyalty member. An invalid member cannot be constructed.
 *
 * @param id unique, non-blank identifier
 * @param name non-blank display name
 * @param tier membership tier, never null
 */
public record Member(String id, String name, MembershipTier tier) {

    public Member {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Member id must not be null or blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Member name must not be null or blank");
        }
        if (tier == null) {
            throw new IllegalArgumentException("Membership tier must not be null");
        }
    }
}
