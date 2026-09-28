package com.stokvault.domain.rules;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Everything a payout rule needs to know, gathered from the database by the PayoutService.
 *
 * @param members            active members, with their rotation position and history
 * @param cycleVerifiedTotal verified money collected in the cycle being paid out (rotational), or null
 * @param balance            money the group currently holds
 * @param benefitAmount      fixed burial benefit configured on the group, or null
 * @param beneficiaryId      the member a burial claim is for, or null
 * @param amountToDistribute amount to share out (investment/grocery); null means "the whole balance"
 */
public record PayoutContext(List<MemberPosition> members, BigDecimal cycleVerifiedTotal, BigDecimal balance,
                            BigDecimal benefitAmount, UUID beneficiaryId, BigDecimal amountToDistribute) {

    /**
     * @param payoutsReceived       non-cancelled payouts this member has had (for the rotation)
     * @param verifiedContributions everything this member has contributed and had verified
     */
    public record MemberPosition(UUID memberId, String name, int payoutPosition, long payoutsReceived,
                                 BigDecimal verifiedContributions) {
    }
}
