package com.stokvault.web;

import com.stokvault.domain.CycleStatus;
import com.stokvault.domain.EligibilityCheck;
import com.stokvault.domain.GroupStatus;
import com.stokvault.domain.GroupType;
import com.stokvault.domain.MembershipRole;
import com.stokvault.domain.PayoutStatus;
import com.stokvault.dto.ContributionView;
import com.stokvault.dto.CycleView;
import com.stokvault.dto.GroupSummary;
import com.stokvault.dto.GroupView;
import com.stokvault.dto.MyPosition;
import com.stokvault.dto.PayoutView;
import com.stokvault.dto.RotationSlot;
import com.stokvault.service.ContributionService;
import com.stokvault.service.CycleService;
import com.stokvault.service.GroupService;
import com.stokvault.service.PayoutService;
import com.stokvault.service.ReportService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * "My stokvels" (SDD 6.1 Member dashboard). Answers "what do I need to do right now?":
 * one card per stokvel with a single status line, most urgent first, and, for treasurers and
 * committee members, a "Needs your attention" list across all their groups.
 * Everything here is read-only: it only calls existing services and turns the answers into words.
 */
@Named
@ViewScoped
public class DashboardBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject private ReportService reports;
    @Inject private GroupService groups;
    @Inject private CycleService cycles;
    @Inject private ContributionService contributions;
    @Inject private PayoutService payouts;
    @Inject private UserSession user;
    @Inject private Format fmt;

    private List<MyPosition> positions;
    private List<GroupView> allGroups;
    private List<Card> cards = List.of();
    private List<Attention> attention = List.of();

    /** One stokvel on the home page. state: OVERDUE, DUE, CHECKING, PAID or NONE. */
    public record Card(UUID groupId, String name, String kind, MembershipRole role, String state, String headline,
                       String detail, boolean pay, String turn, boolean turnIsNext) implements Serializable {

        int urgency() {
            return switch (state) {
                case "OVERDUE" -> 0;
                case "DUE" -> 1;
                case "CHECKING" -> 2;
                default -> 3;
            };
        }
    }

    /** One to-do for a treasurer or committee member, linking to where it is handled. */
    public record Attention(String text, String groupName, String link) implements Serializable {
    }

    // @PostConstruct: runs once when the bean is created, after injection
    @PostConstruct
    void load() {
        positions = reports.myPositions();
        allGroups = groups.list();
        Map<UUID, GroupView> byId = allGroups.stream().collect(Collectors.toMap(GroupView::id, Function.identity()));
        cards = positions.stream()
                .map(p -> card(p, byId.get(p.groupId())))
                .sorted(Comparator.comparingInt(Card::urgency).thenComparing(c -> !c.turnIsNext()).thenComparing(Card::name))
                .toList();
        attention = attention();
    }

    private Card card(MyPosition p, GroupView g) {
        String kind = Ui.label(p.type()) + (p.type() == GroupType.BURIAL ? " society" : " stokvel");
        String state = "NONE";
        String headline;
        String detail;
        boolean pay = false;
        LocalDate due = p.currentCycleDue();

        if (p.groupStatus() == GroupStatus.DRAFT) {
            headline = "Being set up";
            detail = "Nothing to pay yet.";
        } else if (p.groupStatus() == GroupStatus.SUSPENDED) {
            headline = "Paused by the Coop Office";
            detail = "No payments for now.";
        } else if (p.groupStatus() == GroupStatus.CLOSED) {
            headline = "This stokvel has closed";
            detail = "Your history is still here.";
        } else if (due == null) {
            headline = "No payment due right now ✓";
            detail = "Your treasurer opens the next round when it's time.";
        } else if (p.paidCurrentCycle()) {
            state = "PAID";
            LocalDate next = g == null ? null : g.frequency().next(due);
            headline = "All paid ✓";
            detail = next != null && !next.isBefore(LocalDate.now()) ? "Next payment due " + fmt.day(next) : "Nothing to pay right now.";
        } else if (p.myAwaitingVerification() > 0) {
            state = "CHECKING";
            headline = "Treasurer is checking your payment";
            detail = "Nothing to do for now.";
        } else {
            pay = true;
            String round = fmt.round(due, g == null ? null : g.frequency());
            if (fmt.overdue(due)) {
                state = "OVERDUE";
                headline = "Overdue by " + fmt.count(-fmt.daysUntil(due), "day", "days");
                detail = Ui.rand(p.currentCycleAmount()) + " for " + round;
            } else {
                state = "DUE";
                headline = Ui.rand(p.currentCycleAmount()) + " " + fmt.dueText(due);
                detail = "Your " + round + " payment";
            }
            if (p.myArrears() != null && p.currentCycleAmount() != null && p.myArrears().compareTo(p.currentCycleAmount()) > 0) {
                detail += " · you owe " + Ui.rand(p.myArrears()) + " in total";
            }
        }

        // Rotational groups: my place in the payout order (the same list the group page shows)
        String turn = null;
        boolean next = false;
        if (p.type() == GroupType.ROTATIONAL && p.groupStatus() == GroupStatus.ACTIVE) {
            UUID me = user.getMe().id();
            List<RotationSlot> order = reports.rotation(p.groupId());
            RotationSlot mine = order.stream().filter(s -> s.memberId().equals(me)).findFirst().orElse(null);
            if (mine != null) {
                long place = order.stream().filter(s -> (s.status().equals("NEXT") || s.status().equals("UPCOMING"))
                        && s.order() <= mine.order()).count();
                switch (mine.status()) {
                    case "NEXT" -> {
                        next = true;
                        turn = "You're next to receive the payout! " + fmt.cap(fmt.around(mine.date()));
                    }
                    case "IN_PROGRESS" -> {
                        next = true;
                        turn = "Your payout is being prepared!";
                    }
                    case "RECEIVED" -> turn = "You've had your payout this round ✓";
                    default -> turn = "Your payout turn: " + fmt.ordinal((int) place) + " in line · " + fmt.around(mine.date());
                }
            }
        }
        return new Card(p.groupId(), p.groupName(), kind, p.role(), state, headline, detail, pay, turn, next);
    }

    /** To-dos for the groups where I'm the treasurer or on the committee. */
    private List<Attention> attention() {
        List<Attention> items = new ArrayList<>();
        UUID me = user.getMe().id();
        List<PayoutView> approvals = user.isCommittee()
                ? payouts.approvalQueue().stream().map(PayoutView::from).filter(pv -> !pv.memberId().equals(me)).toList()
                : List.of();

        for (MyPosition p : positions) {
            if (p.groupStatus() != GroupStatus.ACTIVE || p.role() == MembershipRole.MEMBER) {
                continue;
            }
            UUID id = p.groupId();
            String link = "group.xhtml?id=" + id;
            GroupSummary summary = reports.summary(id);
            List<ContributionView> unresolved = contributions.list(id, null, null, null).stream()
                    .map(ContributionView::from)
                    .filter(c -> c.verificationStatus().isUnresolved() && !c.memberId().equals(me))
                    .toList();
            List<PayoutView> open = payouts.list(id, null).stream().map(PayoutView::from)
                    .filter(pv -> pv.status().isOpen()).toList();

            if (p.role() == MembershipRole.TREASURER) {
                if (!unresolved.isEmpty()) {
                    items.add(new Attention(fmt.count(unresolved.size(), "payment", "payments") + " to check", p.groupName(), link + "#to-check"));
                }
                CycleView cur = summary.currentCycle();
                if (cur == null || cur.status() != CycleStatus.OPEN) {
                    items.add(new Attention("No round is open for payments", p.groupName(), link));
                } else {
                    long notPaid = cycles.grid(id, cur.id()).rows().stream()
                            .filter(r -> r.status().equals("OUTSTANDING") || r.status().equals("PARTIAL")).count();
                    if (notPaid > 0) {
                        items.add(new Attention(fmt.count(notPaid, "member hasn't", "members haven't") + " paid", p.groupName(), link + "#not-paid"));
                    }
                }
                long ready = open.stream().filter(pv -> pv.status() == PayoutStatus.CONFIRMED
                        || (pv.status() == PayoutStatus.SCHEDULED && pv.eligibilityCheck().allowsProgress())).count();
                if (ready > 0) {
                    items.add(new Attention(fmt.count(ready, "payout", "payouts") + " ready for you", p.groupName(), link + "#payout"));
                }
            } else if (p.role() == MembershipRole.COMMITTEE) {
                // The treasurer can't check their own payment, so a committee member does
                Set<UUID> treasurers = summary.members().stream().filter(s -> s.role() == MembershipRole.TREASURER)
                        .map(GroupSummary.Standing::memberId).collect(Collectors.toSet());
                if (unresolved.stream().anyMatch(c -> treasurers.contains(c.memberId()))) {
                    items.add(new Attention("The treasurer's payment needs checking", p.groupName(), link + "#to-check"));
                }
                for (PayoutView pv : approvals) {
                    if (pv.groupId().equals(id)) {
                        items.add(new Attention("Approve " + Ui.rand(pv.amount()) + " to " + pv.memberName(), p.groupName(), link + "#approve"));
                    }
                }
                long failed = open.stream().filter(pv -> pv.status() == PayoutStatus.SCHEDULED
                        && pv.eligibilityCheck() == EligibilityCheck.FAILED && !pv.memberId().equals(me)).count();
                if (failed > 0) {
                    items.add(new Attention(fmt.count(failed, "payout needs", "payouts need") + " a committee decision", p.groupName(), link + "#approve"));
                }
            }
        }
        return items;
    }

    public List<MyPosition> getPositions() {
        return positions;
    }

    public List<GroupView> getAllGroups() {
        return allGroups;
    }

    public List<Card> getCards() {
        return cards;
    }

    public List<Attention> getAttention() {
        return attention;
    }

    /** How many of my stokvels want a payment from me now (for the page subtitle). */
    public long getDueCount() {
        return cards.stream().filter(Card::pay).count();
    }
}
