package com.brightline.loyalty.service;

import com.brightline.loyalty.domain.MembershipTier;
import java.math.BigDecimal;

/** How one membership tier turns base points into awarded points. */
public interface TierRule {

    /** The tier this rule applies to. */
    MembershipTier tier();

    /**
     * Applies the tier's reward to the base points earned from a purchase.
     *
     * @param basePoints non-negative whole number of points earned before any tier reward
     * @return the whole number of points after the tier reward
     */
    BigDecimal applyTo(BigDecimal basePoints);
}
