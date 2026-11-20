package com.brightline.loyalty.unit;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.brightline.loyalty.domain.MembershipTier;
import com.brightline.loyalty.service.MultiplierTierRule;
import com.brightline.loyalty.service.TierRule;
import com.brightline.loyalty.service.TierRuleRegistry;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TierRuleRegistryTest {

    /**
     * What each tier must turn 10 base points into. Add a line here when a tier is added: the
     * invariant tests catch inconsistent rules, but only a person can state the exact multiplier.
     */
    private static final Map<MembershipTier, BigDecimal> POINTS_FOR_TEN_BASE_POINTS = Map.of(
            MembershipTier.STANDARD, new BigDecimal("10"),
            MembershipTier.SILVER, new BigDecimal("15"),
            MembershipTier.GOLD, new BigDecimal("20"));

    @ParameterizedTest(name = "{0} gets the programme's multiplier")
    @EnumSource(MembershipTier.class)
    @DisplayName("The standard registry holds STANDARD 1x, SILVER 1.5x and GOLD 2x")
    void shouldProvideTheProgrammesMultiplierForEachTier(MembershipTier tier) {
        assertTrue(POINTS_FOR_TEN_BASE_POINTS.containsKey(tier), () -> "Add the expected result "
                + "for the new tier " + tier + " to POINTS_FOR_TEN_BASE_POINTS");
        TierRule rule = TierRuleRegistry.standard().ruleFor(tier);

        assertAll(
                () -> assertEquals(tier, rule.tier()),
                () -> assertEquals(POINTS_FOR_TEN_BASE_POINTS.get(tier),
                        rule.applyTo(BigDecimal.TEN)));
    }

    @Test
    @DisplayName("The registry returns the very rule object that was registered")
    void shouldReturnTheRegisteredRuleInstance() {
        TierRule customGoldRule = new MultiplierTierRule(MembershipTier.GOLD, new BigDecimal("3"));
        List<TierRule> rules = new ArrayList<>(rulesForEveryTierExcept(MembershipTier.GOLD));
        rules.add(customGoldRule);
        TierRuleRegistry registry = new TierRuleRegistry(rules);

        assertSame(customGoldRule, registry.ruleFor(MembershipTier.GOLD));
    }

    @Test
    @DisplayName("A tier without a rule fails at construction, naming the missing tier")
    void shouldRejectRegistryMissingARuleForATier() {
        List<TierRule> rulesWithoutGold = rulesForEveryTierExcept(MembershipTier.GOLD);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new TierRuleRegistry(rulesWithoutGold));

        assertEquals("No rule registered for tier(s): [GOLD]", failure.getMessage());
    }

    @Test
    @DisplayName("Every missing tier is listed, in tier order")
    void shouldListEveryMissingTier() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new TierRuleRegistry(List.of()));

        assertEquals("No rule registered for tier(s): " + Arrays.toString(MembershipTier.values()),
                failure.getMessage());
    }

    @Test
    @DisplayName("Two rules for the same tier are rejected rather than one silently winning")
    void shouldRejectTwoRulesForTheSameTier() {
        List<TierRule> duplicated = List.of(
                new MultiplierTierRule(MembershipTier.GOLD, new BigDecimal("2")),
                new MultiplierTierRule(MembershipTier.GOLD, new BigDecimal("3")));

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new TierRuleRegistry(duplicated));

        assertEquals("More than one rule for tier GOLD", failure.getMessage());
    }

    private static List<TierRule> rulesForEveryTierExcept(MembershipTier excluded) {
        return Arrays.stream(MembershipTier.values())
                .filter(tier -> tier != excluded)
                .<TierRule>map(tier -> new MultiplierTierRule(tier, BigDecimal.ONE))
                .toList();
    }
}
