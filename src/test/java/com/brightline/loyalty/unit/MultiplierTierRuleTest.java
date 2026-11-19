package com.brightline.loyalty.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.brightline.loyalty.domain.MembershipTier;
import com.brightline.loyalty.service.MultiplierTierRule;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MultiplierTierRuleTest {

    @ParameterizedTest(name = "{1} base points x {0} = {2}")
    @CsvSource({
        "1, 0, 0",
        "1, 7, 7",
        "2, 7, 14",
        "1.5, 0, 0",
        "1.5, 1, 1",
        "1.5, 2, 3",
        "1.5, 3, 4",
        "1.5, 5, 7",
        "1.5, 99, 148",
        "2.5, 3, 7",
        "0.5, 3, 1"
    })
    @DisplayName("Multiplies base points and truncates the fraction")
    void shouldMultiplyBasePointsAndTruncate(
            String multiplier, BigDecimal basePoints, BigDecimal expected) {
        MultiplierTierRule rule =
                new MultiplierTierRule(MembershipTier.SILVER, new BigDecimal(multiplier));

        assertEquals(expected, rule.applyTo(basePoints));
    }

    @Test
    @DisplayName("Results beyond the int and long range are returned exactly, not wrapped")
    void shouldReturnExactResultBeyondIntRange() {
        MultiplierTierRule rule = new MultiplierTierRule(MembershipTier.GOLD, new BigDecimal("2"));

        assertEquals(new BigDecimal("4294967294"),
                rule.applyTo(BigDecimal.valueOf(Integer.MAX_VALUE)));
        assertEquals(new BigDecimal("18446744073709551614"),
                rule.applyTo(BigDecimal.valueOf(Long.MAX_VALUE)));
    }

    @Test
    @DisplayName("A rule reports the tier it was built for")
    void shouldExposeTheTierItAppliesTo() {
        MultiplierTierRule rule = new MultiplierTierRule(MembershipTier.SILVER, BigDecimal.ONE);

        assertEquals(MembershipTier.SILVER, rule.tier());
    }

    @Test
    @DisplayName("A rule without a tier is rejected")
    void shouldRejectNullTier() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new MultiplierTierRule(null, BigDecimal.ONE));

        assertEquals("Tier must not be null", failure.getMessage());
    }

    @Test
    @DisplayName("A rule without a multiplier is rejected")
    void shouldRejectNullMultiplier() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new MultiplierTierRule(MembershipTier.GOLD, null));

        assertEquals("Multiplier must be positive: null", failure.getMessage());
    }

    @ParameterizedTest(name = "multiplier {0} is rejected")
    @ValueSource(strings = {"0", "-1", "-0.5"})
    @DisplayName("Zero and negative multipliers are rejected")
    void shouldRejectZeroOrNegativeMultiplier(String multiplier) {
        BigDecimal invalid = new BigDecimal(multiplier);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new MultiplierTierRule(MembershipTier.GOLD, invalid));

        assertEquals("Multiplier must be positive: " + invalid, failure.getMessage());
    }
}
