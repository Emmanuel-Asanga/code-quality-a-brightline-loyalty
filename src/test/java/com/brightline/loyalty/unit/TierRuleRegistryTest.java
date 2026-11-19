package com.brightline.loyalty.unit;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.brightline.loyalty.domain.MembershipTier;
import com.brightline.loyalty.service.MultiplierTierRule;
import com.brightline.loyalty.service.TierRule;
import com.brightline.loyalty.service.TierRuleRegistry;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TierRuleRegistryTest {

    @ParameterizedTest(name = "{0}: 10 base points become {1}")
    @CsvSource({
        "STANDARD, 10",
        "SILVER, 15",
        "GOLD, 20"
    })
    @DisplayName("The standard registry holds STANDARD 1x, SILVER 1.5x and GOLD 2x")
    void shouldProvideTheProgrammesMultiplierForEachTier(
            MembershipTier tier, BigDecimal expected) {
        TierRule rule = TierRuleRegistry.standard().ruleFor(tier);

        assertAll(
                () -> assertEquals(tier, rule.tier()),
                () -> assertEquals(expected, rule.applyTo(BigDecimal.TEN)));
    }

    @Test
    @DisplayName("The registry returns the very rule object that was registered")
    void shouldReturnTheRegisteredRuleInstance() {
        TierRule customGoldRule = new MultiplierTierRule(MembershipTier.GOLD, new BigDecimal("3"));
        TierRuleRegistry registry = new TierRuleRegistry(List.of(
                new MultiplierTierRule(MembershipTier.STANDARD, BigDecimal.ONE),
                new MultiplierTierRule(MembershipTier.SILVER, new BigDecimal("1.5")),
                customGoldRule));

        assertSame(customGoldRule, registry.ruleFor(MembershipTier.GOLD));
    }

    @Test
    @DisplayName("A tier without a rule fails at construction, naming the missing tier")
    void shouldRejectRegistryMissingARuleForATier() {
        List<TierRule> rulesWithoutGold = List.of(
                new MultiplierTierRule(MembershipTier.STANDARD, BigDecimal.ONE),
                new MultiplierTierRule(MembershipTier.SILVER, new BigDecimal("1.5")));

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new TierRuleRegistry(rulesWithoutGold));

        assertEquals("No rule registered for tier(s): [GOLD]", failure.getMessage());
    }

    @Test
    @DisplayName("Every missing tier is listed, in tier order")
    void shouldListEveryMissingTier() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new TierRuleRegistry(List.of()));

        assertEquals("No rule registered for tier(s): [STANDARD, SILVER, GOLD]",
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
}
