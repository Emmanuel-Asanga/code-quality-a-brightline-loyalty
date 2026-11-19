package com.brightline.loyalty;

import com.brightline.loyalty.exception.InvalidPurchaseException;
import com.brightline.loyalty.exception.MemberNotFoundException;
import com.brightline.loyalty.exception.MemberRepositoryException;
import com.brightline.loyalty.repository.InMemoryMemberRepository;
import com.brightline.loyalty.service.PointsCalculator;
import com.brightline.loyalty.service.TierRuleRegistry;
import java.math.BigDecimal;

/** Runs the points service against the sample members and prints the outcome. */
public class Demo {

    public static void main(String[] args) {
        PointsCalculator calculator = new PointsCalculator(
                InMemoryMemberRepository.withSampleMembers(), TierRuleRegistry.standard());

        printPoints(calculator, "m1", new BigDecimal("100"));
        printPoints(calculator, "m2", new BigDecimal("250"));
        printPoints(calculator, "m3", new BigDecimal("600"));
        printPoints(calculator, "unknown", new BigDecimal("100"));
        printPoints(calculator, "m1", new BigDecimal("-5"));
    }

    // The application boundary: the one place that can report an expected failure to a person.
    private static void printPoints(PointsCalculator calculator, String memberId,
            BigDecimal amount) {
        String description = "Member " + memberId + ", GHS " + amount;
        try {
            System.out.println(description + " -> " + calculator.calculatePoints(memberId, amount)
                    + " points");
        } catch (MemberNotFoundException | InvalidPurchaseException
                 | MemberRepositoryException failure) {
            System.out.println(description + " -> FAILED: " + failure.getMessage());
        }
    }
}
