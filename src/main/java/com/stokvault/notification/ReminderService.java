package com.stokvault.notification;

import com.stokvault.domain.CycleStatus;
import com.stokvault.domain.GroupStatus;
import com.stokvault.domain.MembershipStatus;
import com.stokvault.domain.Money;
import com.stokvault.domain.NotificationType;
import com.stokvault.entity.ContributionCycle;
import com.stokvault.entity.Membership;
import com.stokvault.service.LedgerService;
import jakarta.annotation.security.PermitAll;
import jakarta.ejb.Stateless;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Contribution reminders: members who haven't paid an open cycle get a reminder from 3 days
 * before it's due, and daily while it's overdue (at most one per member per cycle per day).
 */
@Stateless
@PermitAll
public class ReminderService {

    static final int DAYS_BEFORE_DUE = 3;

    @PersistenceContext(unitName = "StokVaultPU")
    private EntityManager em;

    @Inject
    private LedgerService ledger;

    @Inject
    private Event<NotificationRequest> notifications;

    /** Returns how many reminders were queued. */
    public int sendDueReminders() {
        LocalDate today = LocalDate.now();
        List<ContributionCycle> cycles = em.createQuery("""
                        SELECT c FROM ContributionCycle c
                        WHERE c.status = :open AND c.group.status = :active AND c.dueDate <= :horizon""", ContributionCycle.class)
                .setParameter("open", CycleStatus.OPEN)
                .setParameter("active", GroupStatus.ACTIVE)
                .setParameter("horizon", today.plusDays(DAYS_BEFORE_DUE))
                .getResultList();
        int sent = 0;
        for (ContributionCycle cycle : cycles) {
            Map<UUID, BigDecimal> paid = ledger.verifiedByMember(cycle);
            Map<UUID, BigDecimal> waiting = ledger.unresolvedByMember(cycle);
            List<Membership> expected = em.createQuery("""
                            SELECT ms FROM Membership ms
                            WHERE ms.group = :group AND ms.status = :active AND ms.joinedDate <= :due""", Membership.class)
                    .setParameter("group", cycle.getGroup())
                    .setParameter("active", MembershipStatus.ACTIVE)
                    .setParameter("due", cycle.getDueDate())
                    .getResultList();
            for (Membership ms : expected) {
                UUID memberId = ms.getMember().getId();
                BigDecimal outstanding = cycle.getAmountDue()
                        .subtract(paid.getOrDefault(memberId, Money.ZERO))
                        .subtract(waiting.getOrDefault(memberId, Money.ZERO));
                if (outstanding.signum() <= 0) {
                    continue;
                }
                boolean overdue = cycle.getDueDate().isBefore(today);
                String text = "StokVault reminder: your " + cycle.getGroup().getName() + " contribution for cycle "
                        + cycle.getCycleNumber() + " (" + Money.format(outstanding) + " outstanding) "
                        + (overdue ? "was due on " + cycle.getDueDate() + " and is overdue." : "is due on " + cycle.getDueDate() + ".");
                notifications.fire(new NotificationRequest(memberId, cycle.getGroup().getId(),
                        NotificationType.CONTRIBUTION_REMINDER, "Contribution reminder", text, null,
                        "REMINDER:" + cycle.getId() + ":" + memberId + ":" + today));
                sent++;
            }
        }
        return sent;
    }
}
