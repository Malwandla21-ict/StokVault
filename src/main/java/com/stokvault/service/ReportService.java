package com.stokvault.service;

import com.stokvault.audit.AuditService;
import com.stokvault.domain.CycleStatus;
import com.stokvault.domain.GroupType;
import com.stokvault.domain.MembershipRole;
import com.stokvault.domain.MembershipStatus;
import com.stokvault.domain.Money;
import com.stokvault.domain.PayoutStatus;
import com.stokvault.domain.VerificationStatus;
import com.stokvault.domain.rules.PayoutContext;
import com.stokvault.domain.rules.RotationalPayoutRule;
import com.stokvault.dto.ActivityItem;
import com.stokvault.dto.GroupSummary;
import com.stokvault.dto.RotationSlot;
import com.stokvault.entity.Contribution;
import com.stokvault.entity.Payout;
import com.stokvault.dto.MyPosition;
import com.stokvault.dto.PlatformReport;
import com.stokvault.entity.ContributionCycle;
import com.stokvault.entity.Member;
import com.stokvault.entity.Membership;
import com.stokvault.entity.StokvelGroup;
import com.stokvault.security.AccessControl;
import com.stokvault.security.Roles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Dashboards and reports: a group's summary, a member's own position across their groups
 * (SDD 6.1), and the Coop Office's platform-wide report.
 *
 * Arrears: for every cycle that has fallen due since the member joined (and before they left),
 * what they still owe = amount due - verified payments for that cycle (never below zero).
 */
@Stateless
@RolesAllowed(Roles.MEMBER)
public class ReportService {

    @PersistenceContext(unitName = "StokVaultPU")
    private EntityManager em;

    @Inject
    private AccessControl access;

    @Inject
    private GroupService groups;

    @Inject
    private MembershipService memberships;

    @Inject
    private CycleService cycles;

    @Inject
    private LedgerService ledger;

    @Inject
    private PayoutService payouts;

    @Inject
    private AuditService audit;

    /** Officers and admins see every member's standing; plain members only their own line. */
    public GroupSummary summary(UUID groupId) {
        StokvelGroup group = groups.find(groupId);
        boolean officer = access.isAdmin() || access.hasRole(group, MembershipRole.TREASURER, MembershipRole.COMMITTEE);
        UUID me = access.currentMemberId().orElse(null);
        List<GroupSummary.Standing> standings = standings(group);
        BigDecimal totalArrears = standings.stream().map(GroupSummary.Standing::arrears).reduce(Money.ZERO, BigDecimal::add);
        List<GroupSummary.Standing> visible = officer ? standings
                : standings.stream().filter(s -> s.memberId().equals(me)).toList();

        return new GroupSummary(groups.view(group), ledger.balance(group), ledger.verifiedTotal(group), ledger.paidOut(group),
                ledger.openPayoutsTotal(group), ledger.unverifiedCount(group), totalArrears,
                cycles.currentCycle(group).map(ledger::view).orElse(null), nextPayout(group), visible);
    }

    /** "My stokvels": the member dashboard. */
    public List<MyPosition> myPositions() {
        Member me = access.currentMember();
        List<Membership> mine = em.createQuery("""
                        SELECT ms FROM Membership ms WHERE ms.member = :me AND ms.status = :active
                        ORDER BY ms.group.name""", Membership.class)
                .setParameter("me", me)
                .setParameter("active", MembershipStatus.ACTIVE)
                .getResultList();
        List<MyPosition> positions = new ArrayList<>();
        for (Membership ms : mine) {
            StokvelGroup group = ms.getGroup();
            GroupSummary.Standing standing = standings(group).stream()
                    .filter(s -> s.memberId().equals(me.getId())).findFirst().orElseThrow();
            Optional<ContributionCycle> open = cycles.openCycle(group);
            boolean paidCurrent = open.map(c -> ledger.verifiedByMember(c).getOrDefault(me.getId(), Money.ZERO)
                    .compareTo(c.getAmountDue()) >= 0).orElse(false);
            long awaiting = em.createQuery("""
                            SELECT COUNT(c) FROM Contribution c
                            WHERE c.group = :group AND c.member = :me AND c.verificationStatus IN :unresolved""", Long.class)
                    .setParameter("group", group).setParameter("me", me)
                    .setParameter("unresolved", List.of(VerificationStatus.PENDING, VerificationStatus.PENDING_REVIEW))
                    .getSingleResult();

            Integer queue = null;
            LocalDate estimate = null;
            if (group.getType() == GroupType.ROTATIONAL) {
                List<PayoutContext.MemberPosition> order = rotationOrder(group);
                for (int i = 0; i < order.size(); i++) {
                    if (order.get(i).memberId().equals(me.getId())) {
                        queue = i + 1;
                        LocalDate due = nextDueDate(group);
                        for (int step = 1; step < queue; step++) {
                            due = group.getFrequency().next(due);
                        }
                        estimate = due;
                    }
                }
            }
            positions.add(new MyPosition(group.getId(), group.getName(), group.getType(), group.getStatus(), ms.getRole(),
                    ledger.balance(group), standing.totalVerified(), standing.arrears(), awaiting, standing.totalReceived(),
                    open.map(ContributionCycle::getDueDate).orElse(null), open.map(ContributionCycle::getAmountDue).orElse(null),
                    paidCurrent, ms.getPayoutPosition(), queue, estimate));
        }
        return positions;
    }

    @RolesAllowed(Roles.ADMIN)
    public PlatformReport platform() {
        List<StokvelGroup> all = em.createQuery("SELECT g FROM StokvelGroup g ORDER BY g.name", StokvelGroup.class).getResultList();
        List<PlatformReport.Row> rows = new ArrayList<>();
        for (StokvelGroup g : all) {
            List<GroupSummary.Standing> standings = standings(g);
            rows.add(new PlatformReport.Row(g.getId(), g.getName(), g.getType(), g.getStatus(),
                    standings.stream().filter(s -> s.status() == MembershipStatus.ACTIVE).count(),
                    ledger.balance(g), ledger.verifiedTotal(g), ledger.paidOut(g),
                    standings.stream().map(GroupSummary.Standing::arrears).reduce(Money.ZERO, BigDecimal::add),
                    ledger.unverifiedCount(g), audit.verify(g).valid()));
        }
        long awaitingApproval = em.createQuery("SELECT COUNT(p) FROM Payout p WHERE p.status = :pending", Long.class)
                .setParameter("pending", PayoutStatus.PENDING_APPROVAL).getSingleResult();
        return new PlatformReport(rows.size(),
                rows.stream().mapToLong(PlatformReport.Row::activeMembers).sum(),
                rows.stream().map(PlatformReport.Row::balance).reduce(Money.ZERO, BigDecimal::add),
                rows.stream().map(PlatformReport.Row::arrears).reduce(Money.ZERO, BigDecimal::add),
                rows.stream().mapToLong(PlatformReport.Row::awaitingVerification).sum(),
                awaitingApproval, rows);
    }

    /**
     * The payout order for the current round of a ROTATIONAL group: who has received this round,
     * whose turn is next, and who is still waiting (with estimated dates). Empty for other types.
     */
    public List<RotationSlot> rotation(UUID groupId) {
        StokvelGroup group = groups.find(groupId);
        if (group.getType() != GroupType.ROTATIONAL) {
            return List.of();
        }
        List<PayoutContext.MemberPosition> members = payouts.positions(group);
        if (members.isEmpty()) {
            return List.of();
        }
        long round = members.stream().mapToLong(PayoutContext.MemberPosition::payoutsReceived).min().orElse(0);

        // Each member's latest (non-cancelled) payout
        Map<UUID, Payout> latest = new HashMap<>();
        em.createQuery("SELECT p FROM Payout p WHERE p.group = :group AND p.status <> :cancelled ORDER BY p.createdAt DESC", Payout.class)
                .setParameter("group", group)
                .setParameter("cancelled", PayoutStatus.CANCELLED)
                .getResultStream()
                .forEach(p -> latest.putIfAbsent(p.getMember().getId(), p));

        BigDecimal pot = Money.of(group.getContributionAmount().multiply(BigDecimal.valueOf(members.size())));
        List<RotationSlot> slots = new ArrayList<>();
        // Already had their turn this round, in the order they were paid
        members.stream()
                .filter(m -> m.payoutsReceived() > round)
                .map(m -> latest.get(m.memberId()))
                .sorted(java.util.Comparator.comparing(Payout::getCreatedAt))
                .forEach(p -> slots.add(new RotationSlot(slots.size() + 1, p.getMember().getId(), p.getMember().getFullName(),
                        positionOf(members, p.getMember().getId()), p.getStatus() == PayoutStatus.PAID ? "RECEIVED" : "IN_PROGRESS",
                        p.getPaidAt() != null ? p.getPaidAt().toLocalDate() : p.getPayoutDate(), p.getAmount())));
        // Still waiting this round, in payout-position order
        LocalDate due = nextDueDate(group);
        List<PayoutContext.MemberPosition> waiting = members.stream()
                .filter(m -> m.payoutsReceived() == round)
                .sorted(RotationalPayoutRule.ROTATION_ORDER)
                .toList();
        for (int i = 0; i < waiting.size(); i++) {
            PayoutContext.MemberPosition m = waiting.get(i);
            slots.add(new RotationSlot(slots.size() + 1, m.memberId(), m.name(), m.payoutPosition(),
                    i == 0 ? "NEXT" : "UPCOMING", due, pot));
            due = group.getFrequency().next(due);
        }
        return slots;
    }

    /** The latest money movements in a group: verified contributions and payouts made. */
    public List<ActivityItem> activity(UUID groupId, int limit) {
        StokvelGroup group = groups.find(groupId);
        List<ActivityItem> items = new ArrayList<>();
        em.createQuery("""
                        SELECT c FROM Contribution c WHERE c.group = :group AND c.verificationStatus = :verified
                        ORDER BY c.verifiedAt DESC""", Contribution.class)
                .setParameter("group", group)
                .setParameter("verified", VerificationStatus.VERIFIED)
                .setMaxResults(limit)
                .getResultStream()
                .forEach(c -> items.add(new ActivityItem("PAID_IN", c.getMember().getFullName(), c.getAmount(),
                        c.getPaymentMethod().name(), c.getContributionDate().atStartOfDay())));
        em.createQuery("SELECT p FROM Payout p WHERE p.group = :group AND p.status = :paid ORDER BY p.paidAt DESC", Payout.class)
                .setParameter("group", group)
                .setParameter("paid", PayoutStatus.PAID)
                .setMaxResults(limit)
                .getResultStream()
                .forEach(p -> items.add(new ActivityItem("PAID_OUT", p.getMember().getFullName(), p.getAmount(),
                        p.getNotes(), p.getPaidAt())));
        items.sort(java.util.Comparator.comparing(ActivityItem::when).reversed());
        return items.stream().limit(limit).toList();
    }

    private static int positionOf(List<PayoutContext.MemberPosition> members, UUID memberId) {
        return members.stream().filter(m -> m.memberId().equals(memberId)).findFirst()
                .map(PayoutContext.MemberPosition::payoutPosition).orElse(0);
    }

    /** Every member's standing in the group (active first, then former members). */
    List<GroupSummary.Standing> standings(StokvelGroup group) {
        LocalDate today = LocalDate.now();
        List<ContributionCycle> dueCycles = cycles.cycles(group).stream()
                .filter(c -> !c.getDueDate().isAfter(today) || c.getStatus() != CycleStatus.OPEN)
                .toList();

        // verified[member][cycle]
        Map<UUID, Map<UUID, BigDecimal>> verified = new HashMap<>();
        em.createQuery("""
                        SELECT c.member.id, c.cycle.id, SUM(c.amount) FROM Contribution c
                        WHERE c.group = :group AND c.verificationStatus = :verified
                        GROUP BY c.member.id, c.cycle.id""", Object[].class)
                .setParameter("group", group)
                .setParameter("verified", VerificationStatus.VERIFIED)
                .getResultStream()
                .forEach(r -> verified.computeIfAbsent((UUID) r[0], k -> new HashMap<>()).put((UUID) r[1], Money.of((BigDecimal) r[2])));
        Map<UUID, BigDecimal> received = em.createQuery("""
                        SELECT p.member.id, SUM(p.amount) FROM Payout p
                        WHERE p.group = :group AND p.status = :paid GROUP BY p.member.id""", Object[].class)
                .setParameter("group", group)
                .setParameter("paid", PayoutStatus.PAID)
                .getResultStream()
                .collect(Collectors.toMap(r -> (UUID) r[0], r -> Money.of((BigDecimal) r[1])));

        return memberships.list(group.getId(), true).stream().map(ms -> {
            UUID id = ms.getMember().getId();
            Map<UUID, BigDecimal> mine = verified.getOrDefault(id, Map.of());
            BigDecimal arrears = Money.ZERO;
            for (ContributionCycle c : dueCycles) {
                boolean expected = !ms.getJoinedDate().isAfter(c.getDueDate())
                        && (ms.getLeftDate() == null || !ms.getLeftDate().isBefore(c.getDueDate()));
                if (expected) {
                    arrears = arrears.add(c.getAmountDue().subtract(mine.getOrDefault(c.getId(), Money.ZERO)).max(Money.ZERO));
                }
            }
            BigDecimal total = mine.values().stream().reduce(Money.ZERO, BigDecimal::add);
            return new GroupSummary.Standing(id, ms.getMember().getFullName(), ms.getRole(), ms.getStatus(),
                    ms.getPayoutPosition(), total, arrears, received.getOrDefault(id, Money.ZERO));
        }).toList();
    }

    private GroupSummary.NextPayout nextPayout(StokvelGroup group) {
        if (group.getType() != GroupType.ROTATIONAL || !group.isActive()) {
            return null;
        }
        List<PayoutContext.MemberPosition> order = rotationOrder(group);
        if (order.isEmpty()) {
            return null;
        }
        PayoutContext.MemberPosition next = order.get(0);
        BigDecimal pot = group.getContributionAmount().multiply(BigDecimal.valueOf(order.size()));
        return new GroupSummary.NextPayout(next.memberId(), next.name(), next.payoutPosition(), Money.of(pot), nextDueDate(group));
    }

    /** Active members in the order they'll be paid (same ordering as RotationalPayoutRule). */
    private List<PayoutContext.MemberPosition> rotationOrder(StokvelGroup group) {
        List<PayoutContext.MemberPosition> order = new ArrayList<>(payouts.positions(group));
        order.sort(RotationalPayoutRule.ROTATION_ORDER);
        return order;
    }

    /** Due date of the cycle that will be paid out next: the open one, else the one after the latest. */
    private LocalDate nextDueDate(StokvelGroup group) {
        return cycles.openCycle(group).map(ContributionCycle::getDueDate)
                .or(() -> cycles.cycles(group).stream().findFirst().map(c -> group.getFrequency().next(c.getDueDate())))
                .orElse(group.getStartDate());
    }
}
