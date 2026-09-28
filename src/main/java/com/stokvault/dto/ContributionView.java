package com.stokvault.dto;

import com.stokvault.domain.PaymentMethod;
import com.stokvault.domain.VerificationStatus;
import com.stokvault.entity.Contribution;
import com.stokvault.entity.Member;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ContributionView(UUID id, UUID groupId, UUID cycleId, int cycleNumber, UUID memberId, String memberName,
                               BigDecimal amount, String paymentReference, PaymentMethod paymentMethod,
                               LocalDate contributionDate, VerificationStatus verificationStatus, String reviewNote,
                               String recordedBy, String verifiedBy, LocalDateTime verifiedAt, LocalDateTime createdAt) {

    public static ContributionView from(Contribution c) {
        return new ContributionView(c.getId(), c.getGroup().getId(), c.getCycle().getId(), c.getCycle().getCycleNumber(),
                c.getMember().getId(), c.getMember().getFullName(), c.getAmount(), c.getPaymentReference(),
                c.getPaymentMethod(), c.getContributionDate(), c.getVerificationStatus(), c.getReviewNote(),
                name(c.getRecordedBy()), name(c.getVerifiedBy()), c.getVerifiedAt(), c.getCreatedAt());
    }

    static String name(Member m) {
        return m == null ? null : m.getFullName();
    }
}
