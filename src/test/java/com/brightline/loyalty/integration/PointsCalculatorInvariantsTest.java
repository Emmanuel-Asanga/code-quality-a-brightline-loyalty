package com.brightline.loyalty.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.brightline.loyalty.domain.Member;
import com.brightline.loyalty.domain.MembershipTier;
import com.brightline.loyalty.repository.InMemoryMemberRepository;
import com.brightline.loyalty.service.PointsCalculator;
import com.brightline.loyalty.service.TierRuleRegistry;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Rules that must hold for <em>any</em> valid purchase and <em>every</em> tier.
 *
 * <p>The example-based tests pin exact values tier by tier. These tests protect against a class
 * of mistake instead, and they run over {@code MembershipTier.values()}: a tier added later is
 * covered by every check below the day it is added, with no test written for it.
 *
 * <p>Nothing here re-implements the calculation. Each check compares outputs with each other. The
 * amounts are a fixed-seed random sample plus a cent-by-cent sweep around the GHS 500 bonus
 * threshold, so a failure is reproducible and its message names the exact amount.
 */
class PointsCalculatorInvariantsTest {

    private static final long SEED = 20_261_120L;
    private static final int RANDOM_SAMPLE_SIZE = 2_000;
    private static final int RANDOM_SAMPLE_BOUND_IN_CENTS = 2_000_000;
    private static final int FLAT_BONUS_POINTS = 50;

    private final PointsCalculator calculator = new PointsCalculator(
            repositoryWithOneMemberPerTier(), TierRuleRegistry.standard());
    private final List<BigDecimal> amounts = sampleAmounts();

    @ParameterizedTest(name = "{0} members never earn negative points")
    @EnumSource(MembershipTier.class)
    @DisplayName("Points are never negative, whatever the amount and tier")
    void shouldNeverAwardNegativePoints(MembershipTier tier) {
        for (BigDecimal amount : amounts) {
            int points = pointsFor(tier, amount);

            assertTrue(points >= 0, () -> describe(tier, amount, points) + " is negative");
        }
    }

    @ParameterizedTest(name = "{0} members never earn fewer points for spending more")
    @EnumSource(MembershipTier.class)
    @DisplayName("Spending more never earns fewer points")
    void shouldNeverAwardFewerPointsForALargerPurchase(MembershipTier tier) {
        List<BigDecimal> ascending = amounts.stream().sorted().toList();

        for (int i = 1; i < ascending.size(); i++) {
            BigDecimal smallerAmount = ascending.get(i - 1);
            BigDecimal largerAmount = ascending.get(i);
            int pointsForSmaller = pointsFor(tier, smallerAmount);
            int pointsForLarger = pointsFor(tier, largerAmount);

            assertTrue(pointsForLarger >= pointsForSmaller,
                    () -> describe(tier, largerAmount, pointsForLarger) + "fewer than the "
                            + pointsForSmaller + " points for the smaller amount " + smallerAmount);
        }
    }

    // GHS 500.00 to 509.99 all earn 50 base points, so the only difference above 500.00
    // is the bonus.
    @ParameterizedTest(name = "{0} members get exactly the flat bonus above GHS 500")
    @EnumSource(MembershipTier.class)
    @DisplayName("Above GHS 500 the bonus is exactly 50 points for every tier, never multiplied")
    void shouldAddExactlyTheFlatBonusAboveFiveHundredForEveryTier(MembershipTier tier) {
        int pointsAtThreshold = pointsFor(tier, new BigDecimal("500.00"));

        for (int cents = 50_001; cents <= 50_999; cents++) {
            BigDecimal amount = BigDecimal.valueOf(cents, 2);

            assertEquals(pointsAtThreshold + FLAT_BONUS_POINTS, pointsFor(tier, amount),
                    () -> describe(tier, amount, pointsFor(tier, amount))
                            + " should be exactly the bonus above GHS 500.00");
        }
    }

    @ParameterizedTest(name = "{0} members earn the same however the amount is written")
    @EnumSource(MembershipTier.class)
    @DisplayName("Trailing zeros never change the points (BigDecimal equals() would)")
    void shouldIgnoreTrailingZerosWhenAwardingPoints(MembershipTier tier) {
        for (BigDecimal amount : amounts) {
            int expected = pointsFor(tier, amount);
            BigDecimal paddedWithZeros = amount.setScale(amount.scale() + 3);
            BigDecimal withoutTrailingZeros = amount.stripTrailingZeros();

            assertEquals(expected, pointsFor(tier, paddedWithZeros),
                    () -> "GHS " + paddedWithZeros + " differs from GHS " + amount);
            assertEquals(expected, pointsFor(tier, withoutTrailingZeros),
                    () -> "GHS " + withoutTrailingZeros + " differs from GHS " + amount);
        }
    }

    private int pointsFor(MembershipTier tier, BigDecimal amount) {
        return calculator.calculatePoints(memberIdFor(tier), amount);
    }

    private static String describe(MembershipTier tier, BigDecimal amount, int points) {
        return tier + " member spending GHS " + amount + " earned " + points + " points: ";
    }

    private static String memberIdFor(MembershipTier tier) {
        return "member-" + tier.name();
    }

    private static InMemoryMemberRepository repositoryWithOneMemberPerTier() {
        List<Member> members = Arrays.stream(MembershipTier.values())
                .map(tier -> new Member(memberIdFor(tier), "Test " + tier, tier))
                .toList();
        return new InMemoryMemberRepository(members);
    }

    private static List<BigDecimal> sampleAmounts() {
        List<BigDecimal> sample = new ArrayList<>();
        Random random = new Random(SEED);
        for (int i = 0; i < RANDOM_SAMPLE_SIZE; i++) {
            sample.add(BigDecimal.valueOf(random.nextInt(RANDOM_SAMPLE_BOUND_IN_CENTS), 2));
        }
        for (int cents = 49_000; cents <= 51_000; cents++) {
            sample.add(BigDecimal.valueOf(cents, 2));
        }
        for (String edge : List.of("0", "0.01", "9.99", "10", "10.00", "500", "500.00")) {
            sample.add(new BigDecimal(edge));
        }
        return sample;
    }
}
