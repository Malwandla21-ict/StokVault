package com.stokvault.dto;

import com.stokvault.domain.EligibilityCheck;
import com.stokvault.domain.PayoutStatus;
import com.stokvault.entity.Payout;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A payout with its eligibility result and approval trail. requiresApproval is true when the
 * amount is above the group's four-eyes threshold.
 */
public record PayoutView(UUID id, UUID groupId, UUID memberId, String memberName, UUID cycleId, Integer cycleNumber,
                         BigDecimal amount, PayoutStatus status, EligibilityCheck eligibilityCheck,
                         LocalDateTime eligibilityCheckedAt, String eligibilityNotes, String overrideReason,
                         String overriddenBy, LocalDate payoutDate, String initiatedBy, String confirmedBy,
                         String approvedBy, LocalDateTime paidAt, String notes, boolean requiresApproval,
                         LocalDateTime createdAt) {

    public static PayoutView from(Payout p) {
        return new PayoutView(p.getId(), p.getGroup().getId(), p.getMember().getId(), p.getMember().getFullName(),
                p.getCycle() == null ? null : p.getCycle().getId(),
                p.getCycle() == null ? null : p.getCycle().getCycleNumber(),
                p.getAmount(), p.getStatus(), p.getEligibilityCheck(), p.getEligibilityCheckedAt(),
                p.getEligibilityNotes(), p.getOverrideReason(), ContributionView.name(p.getOverriddenBy()),
                p.getPayoutDate(), ContributionView.name(p.getInitiatedBy()), ContributionView.name(p.getConfirmedBy()),
                ContributionView.name(p.getApprovedBy()), p.getPaidAt(), p.getNotes(),
                p.getAmount().compareTo(p.getGroup().getApprovalThreshold()) > 0, p.getCreatedAt());
    }
}
