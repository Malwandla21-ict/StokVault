package com.stokvault.service;

import com.stokvault.audit.AuditService;
import com.stokvault.domain.HashChain;
import com.stokvault.domain.MembershipRole;
import com.stokvault.domain.Money;
import com.stokvault.domain.PayoutStatus;
import com.stokvault.domain.VerificationStatus;
import com.stokvault.entity.AuditLog;
import com.stokvault.entity.Contribution;
import com.stokvault.entity.Member;
import com.stokvault.entity.Payout;
import com.stokvault.entity.StokvelGroup;
import com.stokvault.exception.AccessDeniedException;
import com.stokvault.report.Statement;
import com.stokvault.security.AccessControl;
import com.stokvault.security.Roles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Builds the exportable statements (SDD 4.5): a group statement, a member statement and the audit
 * report, each rendered to CSV or PDF by the caller (see ReportResource).
 */
@Stateless
@RolesAllowed(Roles.MEMBER)
public class StatementService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @PersistenceContext(unitName = "StokVaultPU")
    private EntityManager em;

    @Inject
    private AccessControl access;

    @Inject
    private GroupService groups;

    @Inject
    private LedgerService ledger;

    @Inject
    private AuditService audit;

    /** All money in and out of a group in a date range (officers and admins). */
    public Statement groupStatement(UUID groupId, LocalDate from, LocalDate to) {
        StokvelGroup group = groups.find(groupId);
        access.requireRoleOrAdmin(group, MembershipRole.TREASURER, MembershipRole.COMMITTEE);
        return statement(group, null, from, to);
    }

    /** One member's money in and out; members can export their own, officers anyone's. */
    public Statement memberStatement(UUID groupId, UUID memberId, LocalDate from, LocalDate to) {
        StokvelGroup group = groups.find(groupId);
        boolean self = access.currentMemberId().map(memberId::equals).orElse(false);
        if (!self) {
            access.requireRoleOrAdmin(group, MembershipRole.TREASURER, MembershipRole.COMMITTEE);
        }
        Member member = em.find(Member.class, memberId);
        if (member == null) {
            throw new AccessDeniedException("Unknown member");
        }
        return statement(group, member, from, to);
    }

    /** The audit trail with the result of verifying its hash chain (for dispute panels). */
    public Statement auditReport(UUID groupId) {
        StokvelGroup group = groups.find(groupId);
        access.requireRoleOrAdmin(group, MembershipRole.TREASURER, MembershipRole.COMMITTEE);
        HashChain.Verification verification = audit.verify(group);
        List<AuditLog> entries = em.createQuery("SELECT a FROM AuditLog a WHERE a.group = :group ORDER BY a.sequenceNumber", AuditLog.class)
                .setParameter("group", group).getResultList();
        List<List<String>> rows = entries.stream().map(a -> List.of(
                String.valueOf(a.getSequenceNumber()), a.getLoggedAt().format(DATE_TIME), a.getAction(),
                a.getPerformedBy() == null ? "System" : a.getPerformedBy().getFullName(),
                a.getDetails() == null ? "" : a.getDetails(), a.getEntryHash().substring(0, 16))).toList();
        return new Statement("Audit trail: " + group.getName(),
                List.of("Hash-chained, append-only log of every change to this group's configuration, members and money.",
                        "Chain verification: " + (verification.valid() ? "INTACT" : "BROKEN") + " - " + verification.message()),
                List.of("#", "When", "Action", "By", "Details", "Hash (first 16)"), rows,
                List.of(entries.size() + " entries"), List.of(0));
    }

    private Statement statement(StokvelGroup group, Member member, LocalDate from, LocalDate to) {
        LocalDate start = from == null ? LocalDate.of(2000, 1, 1) : from;
        LocalDate end = to == null ? LocalDate.now() : to;

        List<Contribution> contributions = em.createQuery("""
                        SELECT c FROM Contribution c WHERE c.group = :group
                          AND c.contributionDate BETWEEN :start AND :end""", Contribution.class)
                .setParameter("group", group).setParameter("start", start).setParameter("end", end)
                .getResultList().stream()
                .filter(c -> member == null || c.getMember().getId().equals(member.getId()))
                .toList();
        List<Payout> payouts = em.createQuery("""
                        SELECT p FROM Payout p WHERE p.group = :group AND p.status = :paid
                          AND p.paidAt IS NOT NULL""", Payout.class)
                .setParameter("group", group).setParameter("paid", PayoutStatus.PAID)
                .getResultList().stream()
                .filter(p -> member == null || p.getMember().getId().equals(member.getId()))
                .filter(p -> !p.getPaidAt().toLocalDate().isBefore(start) && !p.getPaidAt().toLocalDate().isAfter(end))
                .toList();

        record Entry(LocalDate date, List<String> cells) {
        }
        List<Entry> entries = new ArrayList<>();
        BigDecimal in = Money.ZERO;
        BigDecimal out = Money.ZERO;
        for (Contribution c : contributions) {
            boolean counts = c.getVerificationStatus() == VerificationStatus.VERIFIED;
            if (counts) {
                in = in.add(c.getAmount());
            }
            entries.add(new Entry(c.getContributionDate(), List.of(c.getContributionDate().toString(), "Contribution",
                    c.getMember().getFullName(), "Cycle " + c.getCycle().getCycleNumber(), c.getPaymentReference(),
                    c.getVerificationStatus().name(), c.getAmount().toPlainString(), "")));
        }
        for (Payout p : payouts) {
            out = out.add(p.getAmount());
            entries.add(new Entry(p.getPaidAt().toLocalDate(), List.of(p.getPaidAt().toLocalDate().toString(), "Payout",
                    p.getMember().getFullName(), p.getCycle() == null ? "" : "Cycle " + p.getCycle().getCycleNumber(),
                    p.getNotes() == null ? "" : p.getNotes(), p.getStatus().name(), "", p.getAmount().toPlainString())));
        }
        entries.sort(Comparator.comparing(Entry::date));

        String who = member == null ? group.getName() : member.getFullName() + " - " + group.getName();
        List<String> footer = new ArrayList<>(List.of(
                "Verified contributions in period: " + Money.format(in),
                "Payouts in period: " + Money.format(out)));
        if (member == null) {
            footer.add("Group balance today: " + Money.format(ledger.balance(group)));
        }
        return new Statement((member == null ? "Group statement: " : "Member statement: ") + who,
                List.of("Period: " + (from == null ? "start" : start) + " to " + end,
                        "Only VERIFIED contributions count towards totals and the balance."),
                List.of("Date", "Type", "Member", "Cycle", "Reference / notes", "Status", "In (R)", "Out (R)"),
                entries.stream().map(Entry::cells).toList(), footer, List.of(6, 7));
    }
}
