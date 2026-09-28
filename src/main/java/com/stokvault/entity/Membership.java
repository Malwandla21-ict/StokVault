package com.stokvault.entity;

import com.stokvault.domain.MembershipRole;
import com.stokvault.domain.MembershipStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A member's place in one group: their role, payout position and status (SDD 5.2 "Membership").
 * Leaving a group sets status INACTIVE instead of deleting the row, so history stays intact.
 */
@Entity
@Table(name = "group_memberships", uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "member_id"}))
public class Membership {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "membership_id")
    private UUID id;

    // @ManyToOne: many memberships point at one group. In the database this is a foreign-key
    // column named by @JoinColumn. It's loaded together with the membership (eager by default).
    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private StokvelGroup group;

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MembershipRole role = MembershipRole.MEMBER;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MembershipStatus status = MembershipStatus.ACTIVE;

    // 1 = first in line for a rotational payout
    @Column(name = "payout_position", nullable = false)
    private int payoutPosition;

    @NotNull
    @Column(name = "joined_date", nullable = false)
    private LocalDate joinedDate;

    @Column(name = "left_date")
    private LocalDate leftDate;

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

    public MembershipRole getRole() {
        return role;
    }

    public void setRole(MembershipRole role) {
        this.role = role;
    }

    public MembershipStatus getStatus() {
        return status;
    }

    public void setStatus(MembershipStatus status) {
        this.status = status;
    }

    public int getPayoutPosition() {
        return payoutPosition;
    }

    public void setPayoutPosition(int payoutPosition) {
        this.payoutPosition = payoutPosition;
    }

    public LocalDate getJoinedDate() {
        return joinedDate;
    }

    public void setJoinedDate(LocalDate joinedDate) {
        this.joinedDate = joinedDate;
    }

    public LocalDate getLeftDate() {
        return leftDate;
    }

    public void setLeftDate(LocalDate leftDate) {
        this.leftDate = leftDate;
    }

    public boolean isActive() {
        return status == MembershipStatus.ACTIVE;
    }
}
