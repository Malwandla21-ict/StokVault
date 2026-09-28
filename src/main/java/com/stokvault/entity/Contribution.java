package com.stokvault.entity;

import com.stokvault.domain.Money;
import com.stokvault.domain.PaymentMethod;
import com.stokvault.domain.VerificationStatus;
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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Money a member paid (or says they paid) towards one cycle (SDD 5.2 "Contribution").
 * Only VERIFIED contributions count towards the group's balance.
 */
@Entity
// Indexes from SDD 8.4: contribution lookups by (group, cycle), and member history by member
@Table(name = "cycle_contributions", indexes = {
        @Index(name = "idx_cycle_contributions_group_cycle", columnList = "group_id, cycle_id"),
        @Index(name = "idx_cycle_contributions_member", columnList = "member_id")})
public class Contribution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "contribution_id")
    private UUID id;

    // group_id is also reachable through the cycle, but storing it directly lets every
    // financial query filter by group (tenant isolation) without a join
    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private StokvelGroup group;

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "cycle_id", nullable = false)
    private ContributionCycle cycle;

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @NotNull
    @Positive
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @NotBlank
    @Size(max = 50)
    @Column(name = "payment_reference", nullable = false, length = 50)
    private String paymentReference;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @NotNull
    @Column(name = "contribution_date", nullable = false)
    private LocalDate contributionDate;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private VerificationStatus verificationStatus;

    // Why it was flagged for review, or the treasurer's note when verifying/rejecting
    @Size(max = 255)
    @Column(name = "review_note", length = 255)
    private String reviewNote;

    @ManyToOne
    @JoinColumn(name = "recorded_by")
    private Member recordedBy;

    @ManyToOne
    @JoinColumn(name = "verified_by")
    private Member verifiedBy;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

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

    public ContributionCycle getCycle() {
        return cycle;
    }

    public void setCycle(ContributionCycle cycle) {
        this.cycle = cycle;
    }

    public Member getMember() {
        return member;
    }

    public void setMember(Member member) {
        this.member = member;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = Money.of(amount);
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public void setPaymentReference(String paymentReference) {
        this.paymentReference = paymentReference;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public LocalDate getContributionDate() {
        return contributionDate;
    }

    public void setContributionDate(LocalDate contributionDate) {
        this.contributionDate = contributionDate;
    }

    public VerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(VerificationStatus verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public void setReviewNote(String reviewNote) {
        this.reviewNote = reviewNote;
    }

    public Member getRecordedBy() {
        return recordedBy;
    }

    public void setRecordedBy(Member recordedBy) {
        this.recordedBy = recordedBy;
    }

    public Member getVerifiedBy() {
        return verifiedBy;
    }

    public void setVerifiedBy(Member verifiedBy) {
        this.verifiedBy = verifiedBy;
    }

    public LocalDateTime getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(LocalDateTime verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
