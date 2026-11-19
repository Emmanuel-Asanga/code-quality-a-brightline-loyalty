package com.brightline.loyalty.domain;

/**
 * The membership tiers a loyalty member can hold.
 *
 * <p>This enum only identifies tiers. What each tier earns is defined by a
 * {@code TierRule} registered for it, so adding a tier never changes the calculation algorithm.
 */
public enum MembershipTier {
    STANDARD,
    SILVER,
    GOLD
}
