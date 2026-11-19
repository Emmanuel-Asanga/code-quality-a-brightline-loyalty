package com.brightline.loyalty.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.brightline.loyalty.domain.Member;
import com.brightline.loyalty.domain.MembershipTier;
import com.brightline.loyalty.exception.InvalidPurchaseException;
import com.brightline.loyalty.exception.MemberNotFoundException;
import com.brightline.loyalty.repository.InMemoryMemberRepository;
import com.brightline.loyalty.service.PointsCalculator;
import com.brightline.loyalty.service.TierRuleRegistry;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Wires the real calculator to the real in-memory repository and the real tier rules. No mocks:
 * these tests prove the pieces work together.
 */
class PointsCalculatorIntegrationTest {

    private final PointsCalculator calculator = new PointsCalculator(
            InMemoryMemberRepository.withSampleMembers(), TierRuleRegistry.standard());

    /*
     * Expected values were measured by running the ORIGINAL implementation (before the
     * refactoring) for the same member and amount, so this proves the behaviour was preserved.
     */
    @ParameterizedTest(name = "sample member {0} spending GHS {1} earns {2} points")
    @CsvSource({
        "m1, 100.0, 10",
        "m2, 250.0, 50",
        "m3, 200.0, 30",
        "m3, 30.0, 4",
        "m3, 10.0, 1",
        "m1, 9.99, 0",
        "m2, 500.0, 100",
        "m2, 500.01, 150"
    })
    @DisplayName("Sample members earn exactly what the original implementation awarded")
    void shouldPreserveOriginalBehaviourForSampleMembers(
            String memberId, BigDecimal amount, int expectedPoints) {
        assertEquals(expectedPoints, calculator.calculatePoints(memberId, amount));
    }

    @Test
    @DisplayName("A member supplied by the caller earns points under that member's own tier")
    void shouldApplyTierOfMemberSuppliedToRepository() {
        Member silverMember = new Member("s-9", "Esi Appiah", MembershipTier.SILVER);
        PointsCalculator customCalculator = new PointsCalculator(
                new InMemoryMemberRepository(List.of(silverMember)), TierRuleRegistry.standard());

        assertEquals(140, customCalculator.calculatePoints("s-9", new BigDecimal("600")));
    }

    @Test
    @DisplayName("An unknown member is a MemberNotFoundException through the real repository")
    void shouldReportUnknownMemberThroughRealRepository() {
        MemberNotFoundException failure = assertThrows(MemberNotFoundException.class,
                () -> calculator.calculatePoints("unknown", new BigDecimal("100")));

        assertEquals("unknown", failure.memberId());
    }

    @Test
    @DisplayName("An invalid purchase is rejected end to end")
    void shouldRejectInvalidPurchaseThroughRealCollaborators() {
        InvalidPurchaseException failure = assertThrows(InvalidPurchaseException.class,
                () -> calculator.calculatePoints("m1", new BigDecimal("-100.0")));

        assertEquals("Purchase amount must not be negative: -100.0", failure.getMessage());
    }
}
