package com.brightline.loyalty.service;

import com.brightline.loyalty.domain.MembershipTier;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A {@link TierRule} that multiplies base points by a fixed factor.
 *
 * <p>Fractional results are truncated, not rounded: 3 base points at 1.5x is 4, not 5. This is
 * the loyalty programme's existing behaviour and is deliberate.
  * <p>The rule is intended to receive non-negative base points. RoundingMode.DOWN rounds
 * towards zero, which is relevant if negative values are ever supplied.
 *
 *
 * @param tier the tier this rule applies to
 * @param multiplier a positive factor, for example {@code 1.5}
 */
public record MultiplierTierRule(MembershipTier tier, BigDecimal multiplier)
        implements TierRule {

    public MultiplierTierRule {
        if (tier == null) {
            throw new IllegalArgumentException("Tier must not be null");
        }
        if (multiplier == null || multiplier.signum() <= 0) {
            throw new IllegalArgumentException("Multiplier must be positive: " + multiplier);
        }
    }

    @Override
    public BigDecimal applyTo(BigDecimal basePoints) {
        return basePoints.multiply(multiplier).setScale(0, RoundingMode.DOWN);
    }
}
