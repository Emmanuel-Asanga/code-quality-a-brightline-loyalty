package com.brightline.loyalty.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.brightline.loyalty.domain.Member;
import com.brightline.loyalty.domain.MembershipTier;
import com.brightline.loyalty.exception.InvalidPurchaseException;
import com.brightline.loyalty.exception.MemberNotFoundException;
import com.brightline.loyalty.exception.MemberRepositoryException;
import com.brightline.loyalty.repository.MemberRepository;
import com.brightline.loyalty.service.MultiplierTierRule;
import com.brightline.loyalty.service.PointsCalculator;
import com.brightline.loyalty.service.TierRule;
import com.brightline.loyalty.service.TierRuleRegistry;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link PointsCalculator}. The repository is a Mockito mock; the tier rules are
 * the real, pure {@link TierRuleRegistry#standard()} so every expected value below is checked
 * against the actual calculation.
 */
@ExtendWith(MockitoExtension.class)
class PointsCalculatorTest {

    private static final String MEMBER_ID = "m-1";

    @Mock
    private MemberRepository memberRepository;

    private PointsCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new PointsCalculator(memberRepository, TierRuleRegistry.standard());
    }

    private static BigDecimal ghs(String amount) {
        return new BigDecimal(amount);
    }

    private void givenMemberWithTier(MembershipTier tier) {
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(new Member(MEMBER_ID, "Test Member", tier)));
    }

    // ---------------------------------------------------------------- base points and tiers

    @ParameterizedTest(name = "STANDARD member spending GHS {0} earns {1} points")
    @CsvSource({
        "9.99, 0",
        "10.0, 1",
        "19.99, 1",
        "20.0, 2",
        "99.99, 9",
        "100.0, 10",
        "250.0, 25",
        "499.99, 49"
    })
    @DisplayName("STANDARD members earn one point per GHS 10 (1x multiplier), truncated")
    void shouldAwardCorrectPointsForStandardMember(BigDecimal amount, int expectedPoints) {
        givenMemberWithTier(MembershipTier.STANDARD);

        assertEquals(expectedPoints, calculator.calculatePoints(MEMBER_ID, amount));
    }

    @ParameterizedTest(name = "SILVER member spending GHS {0} earns {1} points")
    @CsvSource({
        "9.99, 0",
        "10.0, 1",
        "20.0, 3",
        "29.0, 3",
        "30.0, 4",
        "40.0, 6",
        "100.0, 15",
        "200.0, 30",
        "499.99, 73"
    })
    @DisplayName("SILVER members earn 1.5x the base points, truncated")
    void shouldAwardCorrectPointsForSilverMember(BigDecimal amount, int expectedPoints) {
        givenMemberWithTier(MembershipTier.SILVER);

        assertEquals(expectedPoints, calculator.calculatePoints(MEMBER_ID, amount));
    }

    @ParameterizedTest(name = "GOLD member spending GHS {0} earns {1} points")
    @CsvSource({
        "9.99, 0",
        "10.0, 2",
        "30.0, 6",
        "100.0, 20",
        "250.0, 50",
        "499.99, 98"
    })
    @DisplayName("GOLD members earn 2x the base points")
    void shouldAwardCorrectPointsForGoldMember(BigDecimal amount, int expectedPoints) {
        givenMemberWithTier(MembershipTier.GOLD);

        assertEquals(expectedPoints, calculator.calculatePoints(MEMBER_ID, amount));
    }

    @Test
    @DisplayName("A zero purchase is valid and earns zero points")
    void shouldAwardZeroPointsForZeroPurchase() {
        givenMemberWithTier(MembershipTier.GOLD);

        assertEquals(0, calculator.calculatePoints(MEMBER_ID, ghs("0")));
    }

    // ---------------------------------------------------------------- rounding

    @Test
    @DisplayName("Fractional points after the multiplier are truncated, not rounded (3 x 1.5 = 4)")
    void shouldTruncateFractionalPointsAfterApplyingSilverMultiplier() {
        givenMemberWithTier(MembershipTier.SILVER);

        assertEquals(4, calculator.calculatePoints(MEMBER_ID, ghs("30")));
    }

    @Test
    @DisplayName("Base points are truncated before the multiplier (GHS 29 -> 2 -> 3, not 2.9 -> 4)")
    void shouldTruncateBasePointsBeforeApplyingMultiplier() {
        givenMemberWithTier(MembershipTier.SILVER);

        assertEquals(3, calculator.calculatePoints(MEMBER_ID, ghs("29")));
    }

    // ---------------------------------------------------------------- bonus

    @Test
    @DisplayName("No bonus when the purchase is exactly GHS 500")
    void shouldNotAwardBonusWhenPurchaseIsExactlyFiveHundred() {
        givenMemberWithTier(MembershipTier.GOLD);

        assertEquals(100, calculator.calculatePoints(MEMBER_ID, ghs("500")));
    }

    @Test
    @DisplayName("Bonus applies as soon as the purchase is above GHS 500")
    void shouldAwardBonusWhenPurchaseIsJustAboveFiveHundred() {
        givenMemberWithTier(MembershipTier.GOLD);

        assertEquals(150, calculator.calculatePoints(MEMBER_ID, ghs("500.01")));
    }

    @Test
    @DisplayName("No bonus just below GHS 500")
    void shouldNotAwardBonusJustBelowFiveHundred() {
        givenMemberWithTier(MembershipTier.STANDARD);

        assertEquals(49, calculator.calculatePoints(MEMBER_ID, ghs("499.99")));
    }

    @ParameterizedTest(name = "{0} member spending GHS {1} earns {2} points including the bonus")
    @CsvSource({
        "STANDARD, 600.0, 110",
        "SILVER, 600.0, 140",
        "GOLD, 600.0, 170"
    })
    @DisplayName("The flat 50-point bonus is added after the multiplier, not multiplied by it")
    void shouldAddFlatBonusAfterTierMultiplier(
            MembershipTier tier, BigDecimal amount, int expectedPoints) {
        givenMemberWithTier(tier);

        assertEquals(expectedPoints, calculator.calculatePoints(MEMBER_ID, amount));
    }

    // ---------------------------------------------------------------- decimal precision

    @Test
    @DisplayName("An exact GHS 90 earns 9 base points where a drifted double lost a point")
    void shouldAwardNineBasePointsForNinetyGhsThatDoubleArithmeticWouldHaveDriftedBelow() {
        double driftedDouble = (90.0 / 1.15) * 1.15;
        assertEquals(89.99999999999999, driftedDouble); // the old input type's failure mode
        BigDecimal rate = new BigDecimal("1.15");
        BigDecimal exactAmount = ghs("90").multiply(rate).divide(rate);
        givenMemberWithTier(MembershipTier.STANDARD);

        assertEquals(9, calculator.calculatePoints(MEMBER_ID, exactAmount));
    }

    @ParameterizedTest(name = "STANDARD member spending GHS {0} earns {1} points")
    @CsvSource({
        "9.999999999999999999, 0",
        "10.000000000000000001, 1",
        "89.99999999999999, 8",
        "90.00, 9",
        "90.000000, 9"
    })
    @DisplayName("Amounts are compared exactly, to any number of decimal places")
    void shouldNotRoundAmountsTowardsAWholePointBoundary(BigDecimal amount, int expectedPoints) {
        givenMemberWithTier(MembershipTier.STANDARD);

        assertEquals(expectedPoints, calculator.calculatePoints(MEMBER_ID, amount));
    }

    @ParameterizedTest(name = "GHS {0} is exactly GHS 500, so earns no bonus")
    @CsvSource({"500", "500.0", "500.00", "5E+2", "0.5E+3"})
    @DisplayName("The bonus threshold ignores trailing zeros (BigDecimal equals() would not)")
    void shouldTreatEveryRepresentationOfFiveHundredAsTheThreshold(BigDecimal amount) {
        givenMemberWithTier(MembershipTier.GOLD);

        assertEquals(100, calculator.calculatePoints(MEMBER_ID, amount));
    }

    @Test
    @DisplayName("The tiniest amount above GHS 500 earns the bonus")
    void shouldAwardBonusForTheSmallestFractionAboveFiveHundred() {
        givenMemberWithTier(MembershipTier.GOLD);

        assertEquals(150, calculator.calculatePoints(MEMBER_ID, ghs("500.000000000000000001")));
    }

    // ---------------------------------------------------------------- range limits

    @Test
    @DisplayName("A large purchase whose points fit in an int is awarded in full")
    void shouldAwardPointsForLargePurchaseWithinIntRange() {
        givenMemberWithTier(MembershipTier.STANDARD);

        assertEquals(1_000_000_050, calculator.calculatePoints(MEMBER_ID, ghs("10000000000")));
    }

    @Test
    @DisplayName("Points equal to Integer.MAX_VALUE are still accepted")
    void shouldAwardMaximumIntPointsWhenTotalEqualsIntMaxValue() {
        givenMemberWithTier(MembershipTier.STANDARD);

        // 2,147,483,597 base points + 50 bonus = Integer.MAX_VALUE exactly
        assertEquals(Integer.MAX_VALUE, calculator.calculatePoints(MEMBER_ID, ghs("21474835970")));
    }

    @Test
    @DisplayName("One point beyond Integer.MAX_VALUE is rejected, not wrapped to a negative number")
    void shouldRejectPurchaseWhenPointsExceedIntRangeByOne() {
        givenMemberWithTier(MembershipTier.STANDARD);

        InvalidPurchaseException failure = assertThrows(InvalidPurchaseException.class,
                () -> calculator.calculatePoints(MEMBER_ID, ghs("21474835980")));

        assertTrue(failure.getMessage().contains("too large"), failure.getMessage());
    }

    @Test
    @DisplayName("A tier multiplier that pushes points past the int range is rejected")
    void shouldRejectPurchaseWhenTierMultiplierPushesPointsOverIntRange() {
        givenMemberWithTier(MembershipTier.GOLD);

        InvalidPurchaseException failure = assertThrows(InvalidPurchaseException.class,
                () -> calculator.calculatePoints(MEMBER_ID, ghs("15000000000")));

        assertTrue(failure.getMessage().contains("too large"), failure.getMessage());
    }

    @ParameterizedTest(name = "GHS {0} is rejected as too large")
    @ValueSource(strings = {"1E+300", "1E+1000"})
    @DisplayName("Astronomically large amounts are rejected instead of overflowing silently")
    void shouldRejectAstronomicallyLargePurchase(BigDecimal amount) {
        givenMemberWithTier(MembershipTier.STANDARD);

        InvalidPurchaseException failure = assertThrows(InvalidPurchaseException.class,
                () -> calculator.calculatePoints(MEMBER_ID, amount));

        assertTrue(failure.getMessage().contains("too large"), failure.getMessage());
    }

    // ---------------------------------------------------------------- validation

    @ParameterizedTest(name = "member id [{0}] is rejected")
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t"})
    @DisplayName("A null, empty or blank member id is rejected before the repository is used")
    void shouldRejectNullOrBlankMemberId(String memberId) {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> calculator.calculatePoints(memberId, ghs("100")));

        assertEquals("Member id must not be null or blank", failure.getMessage());
        verifyNoInteractions(memberRepository);
    }

    @ParameterizedTest(name = "negative amount {0} is rejected")
    @ValueSource(strings = {"-0.01", "-1", "-100", "-1000", "-1E+300"})
    @DisplayName("A negative purchase is rejected, never turned into negative points")
    void shouldRejectNegativePurchase(BigDecimal amount) {
        InvalidPurchaseException failure = assertThrows(InvalidPurchaseException.class,
                () -> calculator.calculatePoints(MEMBER_ID, amount));

        assertEquals("Purchase amount must not be negative: " + amount, failure.getMessage());
        verifyNoInteractions(memberRepository);
    }

    @Test
    @DisplayName("A missing purchase amount is rejected before the repository is used")
    void shouldRejectNullPurchaseAmount() {
        InvalidPurchaseException failure = assertThrows(InvalidPurchaseException.class,
                () -> calculator.calculatePoints(MEMBER_ID, null));

        assertEquals("Purchase amount must not be null", failure.getMessage());
        verifyNoInteractions(memberRepository);
    }

    // ---------------------------------------------------------------- failures

    @Test
    @DisplayName("An unknown member is reported as MemberNotFoundException, not as zero points")
    void shouldThrowMemberNotFoundExceptionWhenMemberDoesNotExist() {
        when(memberRepository.findById("unknown")).thenReturn(Optional.empty());

        MemberNotFoundException failure = assertThrows(MemberNotFoundException.class,
                () -> calculator.calculatePoints("unknown", ghs("100")));

        assertEquals("No member found with id 'unknown'", failure.getMessage());
        assertEquals("unknown", failure.memberId());
    }

    @Test
    @DisplayName("A repository failure reaches the caller unchanged, with its cause intact")
    void shouldPropagateRepositoryFailureWithoutSwallowingIt() {
        IllegalStateException cause = new IllegalStateException("connection refused");
        MemberRepositoryException repositoryFailure =
                new MemberRepositoryException("Could not load member " + MEMBER_ID, cause);
        when(memberRepository.findById(MEMBER_ID)).thenThrow(repositoryFailure);

        MemberRepositoryException thrown = assertThrows(MemberRepositoryException.class,
                () -> calculator.calculatePoints(MEMBER_ID, ghs("100")));

        assertSame(repositoryFailure, thrown);
        assertSame(cause, thrown.getCause());
    }

    @Test
    @DisplayName("An unexpected dependency failure is never turned into zero points")
    void shouldNotConvertUnexpectedDependencyFailureIntoZeroPoints() {
        when(memberRepository.findById(MEMBER_ID))
                .thenThrow(new IllegalStateException("unexpected failure"));

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> calculator.calculatePoints(MEMBER_ID, ghs("100")));

        assertEquals("unexpected failure", thrown.getMessage());
    }

    // ---------------------------------------------------------------- collaboration

    @Test
    @DisplayName("The member is looked up once, by the id that was requested")
    void shouldLookUpMemberExactlyOnceByRequestedId() {
        givenMemberWithTier(MembershipTier.STANDARD);

        int points = calculator.calculatePoints(MEMBER_ID, ghs("100"));

        assertEquals(10, points);
        verify(memberRepository, times(1)).findById(MEMBER_ID);
    }

    @Test
    @DisplayName("Whatever rule is registered for a tier is the one applied (open for extension)")
    void shouldDelegateToTheRuleRegisteredForTheMembersTier() {
        TierRuleRegistry registryWithCustomGoldRule =
                registryWithRule(new FlatRewardRule(MembershipTier.GOLD, 1000));
        PointsCalculator customCalculator =
                new PointsCalculator(memberRepository, registryWithCustomGoldRule);
        givenMemberWithTier(MembershipTier.GOLD);

        // 10 base points + the custom rule's flat 1000; the calculator has no GOLD-specific code
        assertEquals(1010, customCalculator.calculatePoints(MEMBER_ID, ghs("100")));
    }

    // ---------------------------------------------------------------- construction

    @Test
    @DisplayName("A calculator cannot be built without a repository")
    void shouldRejectNullRepository() {
        TierRuleRegistry registry = TierRuleRegistry.standard();

        NullPointerException failure = assertThrows(NullPointerException.class,
                () -> new PointsCalculator(null, registry));

        assertEquals("memberRepository must not be null", failure.getMessage());
    }

    @Test
    @DisplayName("A calculator cannot be built without tier rules")
    void shouldRejectNullTierRules() {
        NullPointerException failure = assertThrows(NullPointerException.class,
                () -> new PointsCalculator(memberRepository, null));

        assertEquals("tierRules must not be null", failure.getMessage());
    }

    /** Gives every tier a 1x rule except {@code customRule}'s tier, so no tier list is assumed. */
    private static TierRuleRegistry registryWithRule(TierRule customRule) {
        List<TierRule> rules = Arrays.stream(MembershipTier.values())
                .<TierRule>map(tier -> tier == customRule.tier()
                        ? customRule
                        : new MultiplierTierRule(tier, BigDecimal.ONE))
                .toList();
        return new TierRuleRegistry(rules);
    }

    /** A rule that is not a multiplier: proves tiers are not limited to one kind of reward. */
    private record FlatRewardRule(MembershipTier tier, long flatReward) implements TierRule {

        @Override
        public BigDecimal applyTo(BigDecimal basePoints) {
            return basePoints.add(BigDecimal.valueOf(flatReward));
        }
    }
}
