package com.stokvault.service;

import com.stokvault.domain.ContributionSchedule;
import com.stokvault.domain.Money;
import com.stokvault.domain.PayoutStatus;
import com.stokvault.dto.MemberStanding;
import com.stokvault.dto.StokvelSummary;
import com.stokvault.entity.Membership;
import com.stokvault.entity.Stokvel;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The stokvel summary: totals, balance, and each member's standing (paid, owed, received).
 */
@Stateless
public class ReportService {

    @PersistenceContext(unitName = "StokVaultPU")
    private EntityManager em;

    @Inject
    private StokvelService stokvelService;

    @Inject
    private MembershipService membershipService;

    @Inject
    private LedgerService ledgerService;

    public StokvelSummary summary(Long stokvelId) {
        Stokvel stokvel = stokvelService.find(stokvelId);
        LocalDate today = LocalDate.now();
        List<Membership> memberships = membershipService.list(stokvelId, true);

        Map<Long, BigDecimal> contributed = sumByMembership("""
                SELECT c.membership.id, SUM(c.amount) FROM Contribution c
                WHERE c.membership.stokvel.id = :id GROUP BY c.membership.id""", stokvelId);
        // Only contributions since the member (last) joined count towards what they owe now
        Map<Long, BigDecimal> contributedSinceJoining = sumByMembership("""
                SELECT c.membership.id, SUM(c.amount) FROM Contribution c
                WHERE c.membership.stokvel.id = :id AND c.contributionDate >= c.membership.joinedOn
                GROUP BY c.membership.id""", stokvelId);
        Map<Long, BigDecimal> received = sumByMembership("""
                SELECT p.membership.id, SUM(p.amount) FROM Payout p
                WHERE p.membership.stokvel.id = :id AND p.status = com.stokvault.domain.PayoutStatus.PAID
                GROUP BY p.membership.id""", stokvelId);

        List<MemberStanding> standings = memberships.stream()
                .map(ms -> standing(stokvel, ms, today,
                        contributed.getOrDefault(ms.getId(), Money.ZERO),
                        contributedSinceJoining.getOrDefault(ms.getId(), Money.ZERO),
                        received.getOrDefault(ms.getId(), Money.ZERO)))
                .toList();

        BigDecimal totalContributions = ledgerService.totalContributions(stokvelId);
        BigDecimal totalPaidOut = ledgerService.totalPayouts(stokvelId, PayoutStatus.PAID);
        BigDecimal totalArrears = standings.stream().map(MemberStanding::arrears)
                .reduce(Money.ZERO, BigDecimal::add);

        return new StokvelSummary(stokvel.getId(), stokvel.getName(), stokvel.getType(), stokvel.getStatus(),
                stokvel.getContributionAmount(), stokvel.getFrequency(),
                memberships.stream().filter(Membership::isActive).count(),
                totalContributions, totalPaidOut,
                ledgerService.totalPayouts(stokvelId, PayoutStatus.SCHEDULED),
                totalContributions.subtract(totalPaidOut), totalArrears, today, standings);
    }

    private static MemberStanding standing(Stokvel stokvel, Membership ms, LocalDate today,
                                           BigDecimal contributed, BigDecimal contributedSinceJoining,
                                           BigDecimal received) {
        // Contributions are due from when both the stokvel and the member had started,
        // until today, or until the member left or the stokvel closed if that was earlier
        LocalDate from = later(stokvel.getStartDate(), ms.getJoinedOn());
        LocalDate until = today;
        if (ms.getLeftOn() != null && ms.getLeftOn().isBefore(until)) {
            until = ms.getLeftOn();
        }
        if (stokvel.getClosedOn() != null && stokvel.getClosedOn().isBefore(until)) {
            until = stokvel.getClosedOn();
        }
        BigDecimal expected = ContributionSchedule.expectedAmount(
                stokvel.getContributionAmount(), stokvel.getFrequency(), from, until);
        BigDecimal arrears = expected.subtract(contributedSinceJoining).max(Money.ZERO);

        return new MemberStanding(ms.getMember().getId(), ms.getMember().getName(), ms.getRole(),
                ms.isActive(), ms.getPayoutPosition(), contributed, expected, arrears, received);
    }

    private Map<Long, BigDecimal> sumByMembership(String jpql, Long stokvelId) {
        return em.createQuery(jpql, Object[].class)
                .setParameter("id", stokvelId)
                .getResultStream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> Money.of((BigDecimal) row[1])));
    }

    private static LocalDate later(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }
}
