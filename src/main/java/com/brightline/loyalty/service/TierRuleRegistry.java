package com.brightline.loyalty.service;

import com.brightline.loyalty.domain.MembershipTier;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds the {@link TierRule} for a tier.
 *
 * <p>The registry refuses to be built unless every {@link MembershipTier} has exactly one rule, so
 * a tier added to the enum without a rule fails at start-up instead of silently earning the wrong
 * points.
 */
public final class TierRuleRegistry {

    private final Map<MembershipTier, TierRule> rulesByTier = new EnumMap<>(MembershipTier.class);

    public TierRuleRegistry(Collection<? extends TierRule> rules) {
        for (TierRule rule : rules) {
            if (rulesByTier.putIfAbsent(rule.tier(), rule) != null) {
                throw new IllegalArgumentException("More than one rule for tier " + rule.tier());
            }
        }
        requireRuleForEveryTier();
    }

    /** The loyalty programme's current rules: STANDARD 1x, SILVER 1.5x, GOLD 2x. */
    public static TierRuleRegistry standard() {
        return new TierRuleRegistry(List.of(
                new MultiplierTierRule(MembershipTier.STANDARD, BigDecimal.ONE),
                new MultiplierTierRule(MembershipTier.SILVER, new BigDecimal("1.5")),
                new MultiplierTierRule(MembershipTier.GOLD, new BigDecimal("2"))));
    }

    public TierRule ruleFor(MembershipTier tier) {
        return rulesByTier.get(tier);
    }

    private void requireRuleForEveryTier() {
        Set<MembershipTier> missing = EnumSet.allOf(MembershipTier.class);
        missing.removeAll(rulesByTier.keySet());
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("No rule registered for tier(s): " + missing);
        }
    }
}
