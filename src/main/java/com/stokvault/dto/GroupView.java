package com.stokvault.dto;

import com.stokvault.domain.ContributionFrequency;
import com.stokvault.domain.GroupStatus;
import com.stokvault.domain.GroupType;
import com.stokvault.domain.MembershipRole;
import com.stokvault.entity.StokvelGroup;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A group as the API shows it. myRole is the caller's role in it (null for admins who aren't members).
 */
public record GroupView(UUID id, String name, String description, GroupType type, BigDecimal contributionAmount,
                        ContributionFrequency frequency, LocalDate startDate, GroupStatus status,
                        BigDecimal approvalThreshold, int completionThreshold, BigDecimal benefitAmount,
                        LocalDateTime createdAt, MembershipRole myRole) {

    public static GroupView from(StokvelGroup g, MembershipRole myRole) {
        return new GroupView(g.getId(), g.getName(), g.getDescription(), g.getType(), g.getContributionAmount(),
                g.getFrequency(), g.getStartDate(), g.getStatus(), g.getApprovalThreshold(),
                g.getCompletionThreshold(), g.getBenefitAmount(), g.getCreatedAt(), myRole);
    }
}
