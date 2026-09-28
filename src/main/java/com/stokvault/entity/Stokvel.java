package com.stokvault.entity;

import com.stokvault.domain.ContributionFrequency;
import com.stokvault.domain.Money;
import com.stokvault.domain.StokvelStatus;
import com.stokvault.domain.StokvelType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A stokvel (savings group) and its rules for how much members pay and how often.
 */
@Entity
@Table(name = "stokvels")
public class Stokvel {

    @Id
    @SequenceGenerator(name = "stokvel_seq", sequenceName = "stokvels_id_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "stokvel_seq")
    private Long id;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Size(max = 500)
    @Column(length = 500)
    private String description;

    // @Enumerated(STRING): store the enum's name ("ROTATING") instead of its position (0),
    // so reordering the enum later can't silently change the meaning of existing rows
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StokvelType type;

    // Money is always BigDecimal, never double (a double can't store 0.10 exactly).
    // precision = 12, scale = 2 -> NUMERIC(12,2): up to 12 digits, 2 of them after the decimal point.
    @NotNull
    @Positive
    @Column(name = "contribution_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal contributionAmount;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContributionFrequency frequency;

    @NotNull
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StokvelStatus status = StokvelStatus.ACTIVE;

    @Column(name = "closed_on")
    private LocalDate closedOn;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() {
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

    public StokvelType getType() {
        return type;
    }

    public void setType(StokvelType type) {
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

    public StokvelStatus getStatus() {
        return status;
    }

    public void setStatus(StokvelStatus status) {
        this.status = status;
    }

    public LocalDate getClosedOn() {
        return closedOn;
    }

    public void setClosedOn(LocalDate closedOn) {
        this.closedOn = closedOn;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isActive() {
        return status == StokvelStatus.ACTIVE;
    }
}
