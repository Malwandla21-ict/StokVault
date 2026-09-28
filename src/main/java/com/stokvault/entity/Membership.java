package com.stokvault.entity;

import com.stokvault.domain.MembershipRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Links a Member to a Stokvel: their role, their place in the payout order, and when they
 * joined or left. Leaving sets leftOn instead of deleting the row, so their contribution
 * history stays intact.
 */
@Entity
// uniqueConstraints: a member can have at most one membership row per stokvel
@Table(name = "memberships",
        uniqueConstraints = @UniqueConstraint(columnNames = {"stokvel_id", "member_id"}))
public class Membership {

    @Id
    @SequenceGenerator(name = "membership_seq", sequenceName = "memberships_id_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "membership_seq")
    private Long id;

    // @ManyToOne: many memberships point to one stokvel. In the database this is a
    // foreign-key column, named by @JoinColumn. optional = false means it's required.
    // Loading a Membership also loads its Stokvel (ManyToOne is fetched eagerly by default).
    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "stokvel_id", nullable = false)
    private Stokvel stokvel;

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MembershipRole role = MembershipRole.MEMBER;

    // 1 = first in line for a payout in a ROTATING stokvel
    @Column(name = "payout_position", nullable = false)
    private int payoutPosition;

    @NotNull
    @Column(name = "joined_on", nullable = false)
    private LocalDate joinedOn;

    @Column(name = "left_on")
    private LocalDate leftOn;

    public Long getId() {
        return id;
    }

    public Stokvel getStokvel() {
        return stokvel;
    }

    public void setStokvel(Stokvel stokvel) {
        this.stokvel = stokvel;
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

    public int getPayoutPosition() {
        return payoutPosition;
    }

    public void setPayoutPosition(int payoutPosition) {
        this.payoutPosition = payoutPosition;
    }

    public LocalDate getJoinedOn() {
        return joinedOn;
    }

    public void setJoinedOn(LocalDate joinedOn) {
        this.joinedOn = joinedOn;
    }

    public LocalDate getLeftOn() {
        return leftOn;
    }

    public void setLeftOn(LocalDate leftOn) {
        this.leftOn = leftOn;
    }

    public boolean isActive() {
        return leftOn == null;
    }
}
