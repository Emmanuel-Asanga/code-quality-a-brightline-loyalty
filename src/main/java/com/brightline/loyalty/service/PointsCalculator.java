package com.brightline.loyalty.service;

import com.brightline.loyalty.domain.Member;
import com.brightline.loyalty.exception.InvalidPurchaseException;
import com.brightline.loyalty.exception.MemberNotFoundException;
import com.brightline.loyalty.repository.MemberRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Works out how many loyalty points a member earns for a purchase.
 *
 * <p>Rules: one base point per GHS 10 spent (whole points only), then the member's tier rule,
 * then a flat 50-point bonus for purchases strictly greater than GHS 500. The bonus is added
 * after the tier rule and is not multiplied by it.
 *
 * <p>Amounts are {@link BigDecimal} so decimal prices are exact: GHS 90.00 is always exactly
 * nine base points, never 8.999999.
 *
 * <p>This class only coordinates. Tier rewards live in {@link TierRule}s, members come from a
 * {@link MemberRepository}, and no failure is caught or hidden here.
 */
public class PointsCalculator {

    private static final BigDecimal AMOUNT_PER_BASE_POINT = BigDecimal.TEN;
    private static final BigDecimal BONUS_THRESHOLD = BigDecimal.valueOf(500);
    private static final BigDecimal BONUS_POINTS = BigDecimal.valueOf(50);
    private static final BigDecimal MAXIMUM_POINTS = BigDecimal.valueOf(Integer.MAX_VALUE);

    private final MemberRepository memberRepository;
    private final TierRuleRegistry tierRules;

    public PointsCalculator(MemberRepository memberRepository, TierRuleRegistry tierRules) {
        this.memberRepository =
                Objects.requireNonNull(memberRepository, "memberRepository must not be null");
        this.tierRules = Objects.requireNonNull(tierRules, "tierRules must not be null");
    }

    /**
     * Calculates the points for a purchase.
     *
     * @param memberId id of the member making the purchase
     * @param amount purchase amount in GHS; zero is allowed and earns zero points. Trailing
     *     zeros do not matter: {@code 500} and {@code 500.00} are the same amount
     * @return the points earned
     * @throws IllegalArgumentException if {@code memberId} is null or blank
     * @throws InvalidPurchaseException if {@code amount} is null, negative, or so large that the
     *     points would not fit in an {@code int}
     * @throws MemberNotFoundException if no member has the given id
     * @throws com.brightline.loyalty.exception.MemberRepositoryException if the repository fails
     */
    public int calculatePoints(String memberId, BigDecimal amount) {
        requireValidMemberId(memberId);
        requireValidAmount(amount);
        Member member = findMember(memberId);
        BigDecimal tierPoints = tierRules.ruleFor(member.tier()).applyTo(basePointsFor(amount));
        return toIntPoints(tierPoints.add(bonusPointsFor(amount)), amount);
    }

    private void requireValidMemberId(String memberId) {
        if (memberId == null || memberId.isBlank()) {
            throw new IllegalArgumentException("Member id must not be null or blank");
        }
    }

    private void requireValidAmount(BigDecimal amount) {
        if (amount == null) {
            throw new InvalidPurchaseException("Purchase amount must not be null");
        }
        if (amount.signum() < 0) {
            throw new InvalidPurchaseException(
                    "Purchase amount must not be negative: " + amount);
        }
    }

    private Member findMember(String memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberNotFoundException(memberId));
    }

    private BigDecimal basePointsFor(BigDecimal amount) {
        return amount.divide(AMOUNT_PER_BASE_POINT, 0, RoundingMode.DOWN);
    }

    // compareTo, not equals: BigDecimal("500.00").equals(BigDecimal("500")) is false
    private BigDecimal bonusPointsFor(BigDecimal amount) {
        return amount.compareTo(BONUS_THRESHOLD) > 0 ? BONUS_POINTS : BigDecimal.ZERO;
    }

    private int toIntPoints(BigDecimal points, BigDecimal amount) {
        if (points.compareTo(MAXIMUM_POINTS) > 0) {
            throw new InvalidPurchaseException("Purchase amount " + amount
                    + " is too large: the points awarded would exceed " + Integer.MAX_VALUE);
        }
        return points.intValueExact();
    }
}
