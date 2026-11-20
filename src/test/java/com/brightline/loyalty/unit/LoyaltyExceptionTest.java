package com.brightline.loyalty.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.brightline.loyalty.exception.InvalidPurchaseException;
import com.brightline.loyalty.exception.LoyaltyException;
import com.brightline.loyalty.exception.MemberNotFoundException;
import com.brightline.loyalty.exception.MemberRepositoryException;
import com.brightline.loyalty.repository.InMemoryMemberRepository;
import com.brightline.loyalty.repository.MemberRepository;
import com.brightline.loyalty.service.PointsCalculator;
import com.brightline.loyalty.service.TierRuleRegistry;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A boundary such as {@code Demo} catches {@link LoyaltyException}. These tests pin which of the
 * calculator's failures that single catch handles, and which must still escape it.
 */
class LoyaltyExceptionTest {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final PointsCalculator calculator = new PointsCalculator(
            InMemoryMemberRepository.withSampleMembers(), TierRuleRegistry.standard());

    @Test
    @DisplayName("An unknown member can be handled as a LoyaltyException")
    void shouldReportUnknownMemberAsLoyaltyException() {
        LoyaltyException failure = assertThrows(LoyaltyException.class,
                () -> calculator.calculatePoints("unknown", HUNDRED));

        assertInstanceOf(MemberNotFoundException.class, failure);
    }

    @Test
    @DisplayName("An invalid purchase can be handled as a LoyaltyException")
    void shouldReportInvalidPurchaseAsLoyaltyException() {
        LoyaltyException failure = assertThrows(LoyaltyException.class,
                () -> calculator.calculatePoints("m1", new BigDecimal("-1")));

        assertInstanceOf(InvalidPurchaseException.class, failure);
    }

    @Test
    @DisplayName("A repository failure can be handled as a LoyaltyException, with its cause kept")
    void shouldReportRepositoryFailureAsLoyaltyException() {
        IllegalStateException cause = new IllegalStateException("connection refused");
        MemberRepository failingRepository = id -> {
            throw new MemberRepositoryException("Could not load member " + id, cause);
        };
        PointsCalculator calculatorWithFailingRepository =
                new PointsCalculator(failingRepository, TierRuleRegistry.standard());

        LoyaltyException failure = assertThrows(LoyaltyException.class,
                () -> calculatorWithFailingRepository.calculatePoints("m1", HUNDRED));

        assertInstanceOf(MemberRepositoryException.class, failure);
        assertEquals("Could not load member m1", failure.getMessage());
        assertSame(cause, failure.getCause());
    }

    @Test
    @DisplayName("A blank member id is a caller bug, so it is NOT a LoyaltyException")
    void shouldNotTreatBlankMemberIdAsLoyaltyFailure() {
        RuntimeException failure = assertThrows(IllegalArgumentException.class,
                () -> calculator.calculatePoints(" ", HUNDRED));

        assertFalse(failure instanceof LoyaltyException);
    }
}
