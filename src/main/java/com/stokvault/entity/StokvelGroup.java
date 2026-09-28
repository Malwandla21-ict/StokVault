package com.stokvault.entity;

import com.stokvault.domain.ContributionFrequency;
import com.stokvault.domain.GroupStatus;
import com.stokvault.domain.GroupType;
import com.stokvault.domain.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A stokvel: one isolated "tenant" on the platform, with its own members, cycles, money and
 * audit trail (SDD 5.2 "StokvelGroup").
 */
@Entity
@Table(name = "stokvel_groups")
public class StokvelGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "group_id")
    private UUID id;

    @NotBlank
    @Size(max = 100)
    @Column(name = "group_name", nullable = false, unique = true, length = 100)
    private String name;

    @Size(max = 500)
    @Column(length = 500)
    private String description;

    // @Enumerated(STRING): stores "ROTATIONAL" rather than a number, so reordering the enum
    // can never silently change what existing rows mean
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "group_type", nullable = false, length = 20)
    private GroupType type;

    // DECIMAL(10,2) as in the SDD: up to 10 digits, 2 of them cents
    @NotNull
    @Positive
    @Column(name = "contribution_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal contributionAmount;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "payout_frequency", nullable = false, length = 20)
    private ContributionFrequency frequency;

    // Due date of the first contribution cycle
    @NotNull
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GroupStatus status = GroupStatus.DRAFT;

    // Four-eyes principle: payouts above this need a committee member's approval
    @NotNull
    @Positive
    @Column(name = "approval_threshold", nullable = false, precision = 10, scale = 2)
    private BigDecimal approvalThreshold;

    // Payout eligibility: % of members who must have paid the cycle in full
    @Min(1)
    @Max(100)
    @Column(name = "completion_threshold", nullable = false)
    private int completionThreshold = 100;

    // Burial groups: the fixed benefit paid per claim
    @Positive
    @Column(name = "benefit_amount", precision = 10, scale = 2)
    private BigDecimal benefitAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public GroupType getType() {
        return type;
    }

    public void setType(GroupType type) {
        this.type = type;
    }

    public BigDecimal getContributionAmount() {
        return contributionAmount;
    }

    public void setContributionAmount(BigDecimal contributionAmount) {
        this.contributionAmount = Money.of(contributionAmount);
    }

    public ContributionFrequency getFrequency() {
        return frequency;
    }

    public void setFrequency(ContributionFrequency frequency) {
        this.frequency = frequency;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public GroupStatus getStatus() {
        return status;
    }

    public void setStatus(GroupStatus status) {
        this.status = status;
    }

    public BigDecimal getApprovalThreshold() {
        return approvalThreshold;
    }

    public void setApprovalThreshold(BigDecimal approvalThreshold) {
        this.approvalThreshold = Money.of(approvalThreshold);
    }

    public int getCompletionThreshold() {
        return completionThreshold;
    }

    public void setCompletionThreshold(int completionThreshold) {
        this.completionThreshold = completionThreshold;
    }

    public BigDecimal getBenefitAmount() {
        return benefitAmount;
    }

    public void setBenefitAmount(BigDecimal benefitAmount) {
        this.benefitAmount = Money.of(benefitAmount);
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isActive() {
        return status == GroupStatus.ACTIVE;
    }
}
