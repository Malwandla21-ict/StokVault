package com.stokvault.entity;

import com.stokvault.domain.EligibilityCheck;
import com.stokvault.domain.Money;
import com.stokvault.domain.PayoutStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Money paid out of a group to a member (SDD 5.2 "Payout"), with the automated eligibility
 * result and everyone who took part in approving it.
 */
@Entity
@Table(name = "group_payouts", indexes = {
        @Index(name = "idx_group_payouts_group", columnList = "group_id"),
        @Index(name = "idx_group_payouts_member", columnList = "member_id")})
public class Payout {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "payout_id")
    private UUID id;

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private StokvelGroup group;

    // The recipient
    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    // The cycle being paid out (rotational groups); null for claims and share-outs
    @ManyToOne
    @JoinColumn(name = "cycle_id")
    private ContributionCycle cycle;

    @NotNull
    @Positive
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PayoutStatus status = PayoutStatus.SCHEDULED;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "eligibility_check", nullable = false, length = 20)
    private EligibilityCheck eligibilityCheck = EligibilityCheck.NOT_RUN;

    // When the batch job last checked this payout, and what it found
    @Column(name = "eligibility_checked_at")
    private LocalDateTime eligibilityCheckedAt;

    @Size(max = 1000)
    @Column(name = "eligibility_notes", length = 1000)
    private String eligibilityNotes;

    @Size(max = 500)
    @Column(name = "override_reason", length = 500)
    private String overrideReason;

    @ManyToOne
    @JoinColumn(name = "overridden_by")
    private Member overriddenBy;

    @NotNull
    @Column(name = "payout_date", nullable = false)
    private LocalDate payoutDate;

    @ManyToOne
    @JoinColumn(name = "initiated_by")
    private Member initiatedBy;

    @ManyToOne
    @JoinColumn(name = "confirmed_by")
    private Member confirmedBy;

    @ManyToOne
    @JoinColumn(name = "approved_by")
    private Member approvedBy;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Size(max = 500)
    @Column(length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public StokvelGroup getGroup() {
        return group;
    }

    public void setGroup(StokvelGroup group) {
        this.group = group;
    }

    public Member getMember() {
        return member;
    }

    public void setMember(Member member) {
        this.member = member;
    }

    public ContributionCycle getCycle() {
        return cycle;
    }

    public void setCycle(ContributionCycle cycle) {
        this.cycle = cycle;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = Money.of(amount);
    }

    public PayoutStatus getStatus() {
        return status;
    }

    public void setStatus(PayoutStatus status) {
        this.status = status;
    }

    public EligibilityCheck getEligibilityCheck() {
        return eligibilityCheck;
    }

    public void setEligibilityCheck(EligibilityCheck eligibilityCheck) {
        this.eligibilityCheck = eligibilityCheck;
    }

    public LocalDateTime getEligibilityCheckedAt() {
        return eligibilityCheckedAt;
    }

    public void setEligibilityCheckedAt(LocalDateTime eligibilityCheckedAt) {
        this.eligibilityCheckedAt = eligibilityCheckedAt;
    }

    public String getEligibilityNotes() {
        return eligibilityNotes;
    }

    public void setEligibilityNotes(String eligibilityNotes) {
        this.eligibilityNotes = eligibilityNotes;
    }

    public String getOverrideReason() {
        return overrideReason;
    }

    public void setOverrideReason(String overrideReason) {
        this.overrideReason = overrideReason;
    }

    public Member getOverriddenBy() {
        return overriddenBy;
    }

    public void setOverriddenBy(Member overriddenBy) {
        this.overriddenBy = overriddenBy;
    }

    public LocalDate getPayoutDate() {
        return payoutDate;
    }

    public void setPayoutDate(LocalDate payoutDate) {
        this.payoutDate = payoutDate;
    }

    public Member getInitiatedBy() {
        return initiatedBy;
    }

    public void setInitiatedBy(Member initiatedBy) {
        this.initiatedBy = initiatedBy;
    }

    public Member getConfirmedBy() {
        return confirmedBy;
    }

    public void setConfirmedBy(Member confirmedBy) {
        this.confirmedBy = confirmedBy;
    }

    public Member getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(Member approvedBy) {
        this.approvedBy = approvedBy;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
