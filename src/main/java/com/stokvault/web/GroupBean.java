package com.stokvault.web;

import com.stokvault.audit.AuditService;
import com.stokvault.domain.ContributionFrequency;
import com.stokvault.domain.CycleStatus;
import com.stokvault.domain.EligibilityCheck;
import com.stokvault.domain.GroupStatus;
import com.stokvault.domain.GroupType;
import com.stokvault.domain.HashChain;
import com.stokvault.domain.MembershipRole;
import com.stokvault.domain.NotificationChannel;
import com.stokvault.domain.PaymentMethod;
import com.stokvault.domain.PayoutStatus;
import com.stokvault.domain.VerificationStatus;
import com.stokvault.dto.ActivityItem;
import com.stokvault.dto.AuditEntryView;
import com.stokvault.dto.ContributionMatrix;
import com.stokvault.dto.RotationSlot;
import com.stokvault.dto.ContributionRequest;
import com.stokvault.dto.ContributionView;
import com.stokvault.dto.CycleGrid;
import com.stokvault.dto.CycleView;
import com.stokvault.dto.GroupRequest;
import com.stokvault.dto.GroupSummary;
import com.stokvault.dto.GroupView;
import com.stokvault.dto.MemberRegistration;
import com.stokvault.dto.MemberView;
import com.stokvault.dto.MembershipRequest;
import com.stokvault.dto.MembershipUpdate;
import com.stokvault.dto.MembershipView;
import com.stokvault.dto.PayoutRunRequest;
import com.stokvault.dto.PayoutView;
import com.stokvault.dto.VerificationDecision;
import com.stokvault.exception.Errors;
import com.stokvault.service.ContributionService;
import com.stokvault.service.CycleService;
import com.stokvault.service.GroupService;
import com.stokvault.service.MemberService;
import com.stokvault.service.MembershipService;
import com.stokvault.service.PayoutService;
import com.stokvault.service.ReportService;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.IOException;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One group's page (app/group.xhtml?id=...&tab=...). The main view ("home") depends on the
 * person's role IN THIS GROUP: members see their payment, their turn and their history;
 * the treasurer sees "This month" (payments to check, who hasn't paid, the payout); committee
 * members also see the decisions waiting for them. Everything else lives under "Manage group"
 * (tab = rounds, payouts, members, records or settings), for officers only.
 * The services enforce the same rules again, so hiding a button is only for convenience,
 * never the security boundary.
 */
@Named
@ViewScoped
public class GroupBean implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Set<String> TABS = Set.of("home", "rounds", "payouts", "members", "records", "settings");
    /** Old tab names (links in older docs and bookmarks) and where they live now. */
    private static final Map<String, String> OLD_TABS = Map.of("overview", "home", "contributions", "rounds", "audit", "records");

    @Inject private GroupService groups;
    @Inject private MembershipService memberships;
    @Inject private CycleService cycles;
    @Inject private ContributionService contributions;
    @Inject private PayoutService payouts;
    @Inject private ReportService reports;
    @Inject private AuditService audit;
    @Inject private MemberService members;
    @Inject private UserSession user;
    @Inject private Format fmt;

    // URL parameters (f:viewParam)
    private UUID id;
    private String tab = "home";

    // Loaded data
    private GroupSummary summary;
    private CycleGrid grid;
    private List<CycleView> cycleList = List.of();
    private List<ContributionView> contributionList = List.of();
    private List<PayoutView> payoutList = List.of();
    private List<MembershipView> memberList = List.of();
    private List<AuditEntryView> auditList = List.of();
    private HashChain.Verification chain;
    private ContributionMatrix matrix;
    private List<RotationSlot> rotation = List.of();
    private List<ActivityItem> activity = List.of();
    private Map<UUID, CycleGrid.Row> gridByMember = Map.of();

    // Per-row inputs (reject/override/cancel reasons, role and position edits), keyed by row id
    private final Map<UUID, String> notes = new HashMap<>();
    private final Map<UUID, String> roleEdits = new HashMap<>();
    private final Map<UUID, String> positionEdits = new HashMap<>();

    // Record contribution form
    private UUID payerId;
    private BigDecimal amount;
    private String reference;
    private PaymentMethod method = PaymentMethod.EFT;
    private LocalDate paidOn = LocalDate.now();

    // Cycle and payout forms
    private LocalDate newCycleDue;
    private UUID beneficiaryId;
    private BigDecimal amountToDistribute;
    private LocalDate payoutDate;
    private String runNotes;

    // Add member / register person forms
    private String lookup;
    private MemberView found;
    private MembershipRole addRole = MembershipRole.MEMBER;
    private LocalDate addJoined = LocalDate.now();
    private String regName;
    private String regNationalId;
    private String regPhone;
    private String regEmail;
    private NotificationChannel regChannel = NotificationChannel.SMS;
    private boolean regConsent;

    // Settings form
    private String setName;
    private String setDescription;
    private GroupType setType;
    private BigDecimal setAmount;
    private ContributionFrequency setFrequency;
    private LocalDate setStart;
    private BigDecimal setApprovalThreshold;
    private Integer setCompletion;
    private BigDecimal setBenefit;
    private String statusReason;

    /** f:viewAction: runs on the first (non-postback) request for the page. */
    public void load() throws IOException {
        tab = OLD_TABS.getOrDefault(tab, tab);
        if (!TABS.contains(tab)) {
            tab = "home";
        }
        if (id == null) {
            FacesContext.getCurrentInstance().getExternalContext().redirect("index.xhtml");
            return;
        }
        try {
            reload();
            if (!tab.equals("home") && !isOfficer()) {
                tab = "home"; // "Manage group" is for the treasurer, committee and Coop Office
                reload();
            }
        } catch (RuntimeException e) {
            Ui.error(Errors.message(e));
            FacesContext.getCurrentInstance().getExternalContext().redirect("index.xhtml");
        }
    }

    private void reload() {
        summary = reports.summary(id);
        grid = null;
        gridByMember = Map.of();
        // Used on every view: this round's payers, the payments I may see (all of them for
        // officers, my own for members), the rounds (to name them by month), payouts and members
        contributionList = contributions.list(id, null, null, null).stream().map(ContributionView::from).toList();
        cycleList = cycles.list(id);
        payoutList = payouts.list(id, null).stream().map(PayoutView::from).toList();
        rotation = reports.rotation(id);
        memberList = memberships.list(id, tab.equals("members")).stream().map(MembershipView::from).toList();
        memberList.forEach(m -> {
            roleEdits.putIfAbsent(m.memberId(), m.role().name());
            positionEdits.putIfAbsent(m.memberId(), String.valueOf(m.payoutPosition()));
        });
        if (summary.currentCycle() != null) {
            grid = cycles.grid(id, summary.currentCycle().id());
            gridByMember = grid.rows().stream().collect(java.util.stream.Collectors.toMap(CycleGrid.Row::memberId, r -> r));
        }
        if (amount == null && summary.currentCycle() != null) {
            // The payment window opens with what I still owe this round (or the round's amount)
            BigDecimal owed = getMyOwed();
            amount = owed.signum() > 0 && !isTreasurer() ? owed : summary.currentCycle().amountDue();
        }
        if (payerId == null && isTreasurer() && !getUnpaidOthers().isEmpty()) {
            payerId = getUnpaidOthers().get(0).memberId(); // "Record payment" opens on the first unpaid member
        }
        if (tab.equals("rounds")) {
            matrix = cycles.matrix(id, 6);
        }
        if (tab.equals("records") && isOfficer()) {
            auditList = audit.entries(groups.find(id), 200).stream().map(AuditEntryView::from).toList();
            chain = audit.verify(groups.find(id));
        }
        if (tab.equals("settings")) {
            GroupView g = summary.group();
            setName = g.name();
            setDescription = g.description();
            setType = g.type();
            setAmount = g.contributionAmount();
            setFrequency = g.frequency();
            setStart = g.startDate();
            setApprovalThreshold = g.approvalThreshold();
            setCompletion = g.completionThreshold();
            setBenefit = g.benefitAmount();
        }
    }

    private void act(Runnable action, String success) {
        if (Ui.attempt(action, success)) {
            notes.clear();
        }
        reload();
    }

    // ---- roles (for showing the right buttons) ----

    public GroupView getGroup() {
        return summary.group();
    }

    public boolean isTreasurer() {
        return summary.group().myRole() == MembershipRole.TREASURER;
    }

    public boolean isCommittee() {
        return summary.group().myRole() == MembershipRole.COMMITTEE;
    }

    public boolean isAdmin() {
        return user.isAdmin();
    }

    public boolean isOfficer() {
        return isTreasurer() || isCommittee() || isAdmin();
    }

    public boolean isActiveGroup() {
        return summary.group().status() == GroupStatus.ACTIVE;
    }

    public boolean isMemberOfGroup() {
        return summary.group().myRole() != null;
    }

    /** "Manage group" and the sub-sections under it. */
    public boolean isManage() {
        return !tab.equals("home");
    }

    /** The treasurer's "This month" board; the Coop Office sees it too (read-only) when not a member. */
    public boolean isShowBoard() {
        return isTreasurer() || (isAdmin() && !isMemberOfGroup());
    }

    // ---- read-only helpers for the simplified pages (they only reshape data loaded above) ----

    private UUID me() {
        return user.getMe().id();
    }

    /** My line for the current round (PAID, AWAITING_VERIFICATION, PARTIAL, OUTSTANDING), or null. */
    public CycleGrid.Row getMyRow() {
        return gridByMember.get(me());
    }

    /** What I still have to pay this round (nothing if paid or waiting to be checked). */
    public BigDecimal getMyOwed() {
        CycleGrid.Row row = getMyRow();
        if (row == null) {
            return BigDecimal.ZERO;
        }
        return row.amountDue().subtract(row.verified()).subtract(row.awaitingVerification()).max(BigDecimal.ZERO);
    }

    /** Can I pay now? (an open round, and I still owe something for it) */
    public boolean isCanPay() {
        return isCanRecord() && getMyOwed().signum() > 0;
    }

    /** My payments, newest first. */
    public List<ContributionView> getMyContributions() {
        UUID me = me();
        return contributionList.stream().filter(c -> c.memberId().equals(me)).toList();
    }

    /** A payment of mine in the current round that the treasurer did not accept, if nothing replaced it. */
    public ContributionView getMyRejected() {
        CycleGrid.Row row = getMyRow();
        if (row == null || row.status().equals("PAID") || row.status().equals("AWAITING_VERIFICATION")) {
            return null;
        }
        UUID cycleId = summary.currentCycle().id();
        return getMyContributions().stream()
                .filter(c -> c.cycleId().equals(cycleId) && c.verificationStatus() == VerificationStatus.REJECTED)
                .findFirst().orElse(null);
    }

    /** The round a payment belongs to, by name ("September"), instead of "Cycle 4". */
    public String roundOf(UUID cycleId) {
        return cycleList.stream().filter(cy -> cy.id().equals(cycleId)).findFirst()
                .map(cy -> fmtRound(cy.dueDate())).orElse("");
    }

    private String fmtRound(LocalDate due) {
        return fmt.round(due, summary.group().frequency());
    }

    /** The name of the round that opens next ("October"), for the "Open the ... round" button. */
    public String getNextRoundName() {
        CycleView cur = summary.currentCycle();
        LocalDate due = cur == null ? summary.group().startDate() : summary.group().frequency().next(cur.dueDate());
        return fmtRound(due);
    }

    /** My place in the payout line (rotational groups), or null. */
    public RotationSlot getMySlot() {
        UUID me = me();
        return rotation.stream().filter(s -> s.memberId().equals(me)).findFirst().orElse(null);
    }

    /** How many people are ahead of me, plus one: "5th in line". Only for people still waiting. */
    public int getMyPlaceInLine() {
        RotationSlot mine = getMySlot();
        if (mine == null) {
            return 0;
        }
        return (int) rotation.stream()
                .filter(s -> (s.status().equals("NEXT") || s.status().equals("UPCOMING")) && s.order() <= mine.order())
                .count();
    }

    /** "To check": payments waiting for a decision that this person should look at. */
    public List<ContributionView> getToCheck() {
        List<ContributionView> unresolved = getUnresolved();
        if (isCommittee()) {
            // The treasurer can't check their own payment, so that one comes to the committee
            Set<UUID> treasurers = memberList.stream().filter(m -> m.role() == MembershipRole.TREASURER)
                    .map(MembershipView::memberId).collect(java.util.stream.Collectors.toSet());
            return unresolved.stream().filter(c -> treasurers.contains(c.memberId())).toList();
        }
        return isShowBoard() ? unresolved : List.of();
    }

    /** "Not paid yet": this round's members with nothing (or only part) verified and nothing waiting. */
    public List<CycleGrid.Row> getNotPaid() {
        return getThisMonth().stream().filter(r -> r.status().equals("OUTSTANDING") || r.status().equals("PARTIAL")).toList();
    }

    /** "Paid ✓": this round's members who have paid in full. */
    public List<CycleGrid.Row> getPaidRows() {
        return getThisMonth().stream().filter(r -> r.status().equals("PAID")).toList();
    }

    /** Payouts still in progress (planned, waiting for approval or ready to pay). */
    public List<PayoutView> getOpenPayouts() {
        return payoutList.stream().filter(p -> p.status().isOpen()).toList();
    }

    /** The first three payouts in progress (the payout card; the rest are under Manage group). */
    public List<PayoutView> getFirstOpenPayouts() {
        return getOpenPayouts().stream().limit(3).toList();
    }

    /** Payouts waiting for this committee member: to approve, or checks failed and needing a decision. */
    public List<PayoutView> getForCommittee() {
        return payoutList.stream().filter(p -> canApprove(p) || canOverride(p)).toList();
    }

    /** Active members (the context card and "More about this group"). */
    public long getMemberCount() {
        return memberList.stream().filter(m -> m.status().name().equals("ACTIVE")).count();
    }

    public boolean isMe(UUID memberId) {
        return me().equals(memberId);
    }

    // ---- contributions ----

    public void recordContribution() {
        UUID payer = isTreasurer() ? payerId : null;
        act(() -> {
            ContributionService.Recorded recorded = contributions.record(id,
                    new ContributionRequest(payer, null, amount, reference, method, paidOn));
            VerificationStatus status = recorded.contribution().getVerificationStatus();
            String note = recorded.contribution().getReviewNote();
            if (!recorded.created()) {
                Ui.info("That payment was already recorded (same person, round and reference), so nothing changed");
            } else if (status == VerificationStatus.VERIFIED) {
                Ui.info("Payment recorded ✓");
            } else if (status == VerificationStatus.PENDING_REVIEW) {
                Ui.info("Payment recorded, but it needs a second look" + (note == null ? "" : ": " + note));
            } else {
                Ui.info(payer == null && !isTreasurer()
                        ? "Thank you! Your treasurer will check your payment against the bank statement."
                        : "Payment recorded. A committee member will check it.");
            }
            reference = null;
        }, null);
    }

    public void verify(UUID contributionId) {
        act(() -> contributions.verify(id, contributionId, new VerificationDecision(VerificationStatus.VERIFIED, notes.get(contributionId))),
                "Marked as paid ✓");
    }

    public void reject(UUID contributionId) {
        String reason = notes.get(contributionId);
        if (reason == null || reason.isBlank()) {
            Ui.error("Say why you are not accepting this payment. The member will see the reason.");
            return;
        }
        act(() -> contributions.verify(id, contributionId, new VerificationDecision(VerificationStatus.REJECTED, notes.get(contributionId))),
                "Payment not accepted. The member has been told why.");
    }

    public List<ContributionView> getUnresolved() {
        return contributionList.stream().filter(c -> c.verificationStatus().isUnresolved()).toList();
    }

    /** This cycle's status for a member (PAID, AWAITING_VERIFICATION, PARTIAL, OUTSTANDING), or null. */
    public String cycleStatus(UUID memberId) {
        CycleGrid.Row row = gridByMember.get(memberId);
        return row == null ? null : row.status();
    }

    /** Current-cycle rows, unpaid first (the "This month" list). */
    public List<CycleGrid.Row> getThisMonth() {
        if (grid == null) {
            return List.of();
        }
        return grid.rows().stream()
                .sorted(java.util.Comparator.comparing((CycleGrid.Row r) -> r.status().equals("PAID")).thenComparing(CycleGrid.Row::payoutPosition))
                .toList();
    }

    /** Who the treasurer can record a payment for: everyone who hasn't paid the cycle in full. */
    public List<CycleGrid.Row> getUnpaid() {
        return getThisMonth().stream().filter(r -> !r.status().equals("PAID")).toList();
    }

    /** Unpaid members other than me (the treasurer records their own payment as "Me"). */
    public List<CycleGrid.Row> getUnpaidOthers() {
        UUID me = user.getMe().id();
        return getUnpaid().stream().filter(r -> !r.memberId().equals(me)).toList();
    }

    /** My own line in the standings, or null if I'm not a member (e.g. an admin). */
    public GroupSummary.Standing getMyStanding() {
        UUID me = user.getMe().id();
        return summary.members().stream().filter(s -> s.memberId().equals(me)).findFirst().orElse(null);
    }

    public boolean isCanRecord() {
        return isActiveGroup() && isCycleOpen() && isMemberOfGroup();
    }

    public int getActiveMemberCount() {
        return (int) summary.members().stream().filter(m -> m.status().name().equals("ACTIVE")).count();
    }

    public boolean canVerify(ContributionView c) {
        return (isTreasurer() || isCommittee()) && c.verificationStatus().isUnresolved()
                && !c.memberId().equals(user.getMe().id());
    }

    // ---- cycles ----

    public void openCycle() {
        act(() -> cycles.open(id, newCycleDue), "The new round is open. Members can now pay.");
        newCycleDue = null;
    }

    public void closeCycle(UUID cycleId) {
        act(() -> cycles.close(id, cycleId), "Round closed");
    }

    public void reconcileCycle(UUID cycleId) {
        act(() -> cycles.reconcile(id, cycleId), "Round marked as balanced ✓");
    }

    public boolean isCycleOpen() {
        return summary.currentCycle() != null && summary.currentCycle().status() == CycleStatus.OPEN;
    }

    // ---- payouts ----

    public void runPayout() {
        act(() -> {
            int count = payouts.run(id, new PayoutRunRequest(null, beneficiaryId, amountToDistribute, payoutDate, runNotes)).size();
            Ui.info((count == 1 ? "Payout planned." : count + " payouts planned.")
                    + " StokVault is now checking that everyone has paid. Refresh in a moment to see the result.");
            runNotes = null;
            beneficiaryId = null;
        }, null);
    }

    public void checkEligibility() {
        act(() -> payouts.startEligibilityCheck(id), "Checking again. Refresh in a moment to see the result.");
    }

    public void confirm(UUID payoutId) {
        act(() -> {
            PayoutStatus result = payouts.confirm(id, payoutId).getStatus();
            Ui.info(result == PayoutStatus.PENDING_APPROVAL
                    ? "Confirmed. It is a big payout, so a committee member must approve it too."
                    : "Confirmed ✓ You can pay it out now.");
        }, null);
    }

    public void approve(UUID payoutId) {
        act(() -> payouts.approve(id, payoutId), "Approved ✓ The treasurer can now pay it out.");
    }

    public void override(UUID payoutId) {
        String reason = notes.get(payoutId);
        if (reason == null || reason.isBlank()) {
            Ui.error("Say why the committee is allowing this payout. It is kept in the records.");
            return;
        }
        act(() -> payouts.override(id, payoutId, reason), "Allowed. Your reason is kept in the records.");
    }

    public void pay(UUID payoutId) {
        act(() -> payouts.pay(id, payoutId), "Marked as paid out ✓ The member has been sent an SMS.");
    }

    public void cancel(UUID payoutId) {
        act(() -> payouts.cancel(id, payoutId, notes.get(payoutId)), "Payout cancelled");
    }

    /** Committee: say no to a big payout (it is cancelled, with the reason). */
    public void decline(UUID payoutId) {
        String reason = notes.get(payoutId);
        if (reason == null || reason.isBlank()) {
            Ui.error("Say why you are declining. It is kept in the records.");
            return;
        }
        act(() -> payouts.cancel(id, payoutId, "Declined by committee: " + reason), "Payout declined");
    }

    public boolean canOverride(PayoutView p) {
        return isCommittee() && p.status() == PayoutStatus.SCHEDULED && p.eligibilityCheck() == EligibilityCheck.FAILED
                && !p.memberId().equals(user.getMe().id());
    }

    public boolean canApprove(PayoutView p) {
        return isCommittee() && p.status() == PayoutStatus.PENDING_APPROVAL && !p.memberId().equals(user.getMe().id());
    }

    // ---- members ----

    public void lookupMember() {
        found = null;
        Ui.attempt(() -> {
            List<MemberView> results = members.search(lookup).stream().map(MemberView::from).toList();
            if (results.isEmpty()) {
                Ui.info("Nobody is registered with that number. Register them below.");
                regPhone = lookup;
            } else {
                found = results.get(0);
            }
        }, null);
    }

    public void addMember() {
        if (found == null) {
            return;
        }
        UUID memberId = found.id();
        act(() -> memberships.add(id, new MembershipRequest(memberId, addRole, addJoined)), found.fullName() + " added");
        found = null;
        lookup = null;
    }

    public void registerAndAdd() {
        act(() -> {
            MemberView created = MemberView.from(members.register(new MemberRegistration(regName, regNationalId, regPhone,
                    regEmail, regChannel, regConsent)));
            memberships.add(id, new MembershipRequest(created.id(), addRole, addJoined));
            Ui.info(created.fullName() + " registered and added. They sign in the first time with an SMS code.");
            regName = null;
            regNationalId = null;
            regPhone = null;
            regEmail = null;
            regConsent = false;
        }, null);
    }

    public void saveRole(UUID memberId) {
        act(() -> memberships.update(id, memberId, new MembershipUpdate(MembershipRole.valueOf(roleEdits.get(memberId)), null)), "Role updated");
    }

    public void savePosition(UUID memberId) {
        act(() -> memberships.update(id, memberId, new MembershipUpdate(null, parsePosition(positionEdits.get(memberId)))), "Payout order updated");
    }

    private static Integer parsePosition(String text) {
        try {
            return Integer.valueOf(text.trim());
        } catch (NumberFormatException | NullPointerException e) {
            return null; // leaves the position unchanged
        }
    }

    public void removeMember(UUID memberId) {
        act(() -> memberships.remove(id, memberId), "Member removed from the group (their history is kept)");
    }

    public List<MembershipView> getActiveMembers() {
        return memberList.stream().filter(m -> m.status().name().equals("ACTIVE")).toList();
    }

    /** Everyone except me (the treasurer's "Member" list already starts with "Me"). */
    public List<MembershipView> getOtherActiveMembers() {
        UUID me = user.getMe().id();
        return getActiveMembers().stream().filter(m -> !m.memberId().equals(me)).toList();
    }

    // ---- settings and status ----

    public void saveSettings() {
        act(() -> groups.update(id, new GroupRequest(setName, setDescription, setType, setAmount, setFrequency, setStart,
                setApprovalThreshold, setCompletion, setBenefit)), "Settings saved");
    }

    public void activate() {
        act(() -> groups.activate(id), "Group activated");
    }

    public void suspend() {
        act(() -> groups.suspend(id, statusReason), "Group suspended");
    }

    public void resume() {
        act(() -> groups.resume(id), "Group resumed");
    }

    public void closeGroup() {
        act(() -> groups.close(id, statusReason), "Group closed");
    }

    // ---- choices for drop-downs ----

    public PaymentMethod[] getPaymentMethods() {
        return PaymentMethod.values();
    }

    public MembershipRole[] getRoles() {
        return MembershipRole.values();
    }

    public NotificationChannel[] getChannels() {
        return new NotificationChannel[]{NotificationChannel.SMS, NotificationChannel.WHATSAPP};
    }

    public GroupType[] getTypes() {
        return GroupType.values();
    }

    public ContributionFrequency[] getFrequencies() {
        return ContributionFrequency.values();
    }

    // ---- getters / setters ----

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getTab() { return tab; }
    public void setTab(String tab) { this.tab = tab; }
    public GroupSummary getSummary() { return summary; }
    public CycleGrid getGrid() { return grid; }
    public List<CycleView> getCycleList() { return cycleList; }
    public List<ContributionView> getContributionList() { return contributionList; }
    public List<PayoutView> getPayoutList() { return payoutList; }
    public List<MembershipView> getMemberList() { return memberList; }
    public List<AuditEntryView> getAuditList() { return auditList; }
    public HashChain.Verification getChain() { return chain; }
    public ContributionMatrix getMatrix() { return matrix; }
    public List<RotationSlot> getRotation() { return rotation; }
    public List<ActivityItem> getActivity() { return activity; }
    public Map<UUID, String> getNotes() { return notes; }
    public Map<UUID, String> getRoleEdits() { return roleEdits; }
    public Map<UUID, String> getPositionEdits() { return positionEdits; }
    public UUID getPayerId() { return payerId; }
    public void setPayerId(UUID payerId) { this.payerId = payerId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public PaymentMethod getMethod() { return method; }
    public void setMethod(PaymentMethod method) { this.method = method; }
    public LocalDate getPaidOn() { return paidOn; }
    public void setPaidOn(LocalDate paidOn) { this.paidOn = paidOn; }
    public LocalDate getNewCycleDue() { return newCycleDue; }
    public void setNewCycleDue(LocalDate newCycleDue) { this.newCycleDue = newCycleDue; }
    public UUID getBeneficiaryId() { return beneficiaryId; }
    public void setBeneficiaryId(UUID beneficiaryId) { this.beneficiaryId = beneficiaryId; }
    public BigDecimal getAmountToDistribute() { return amountToDistribute; }
    public void setAmountToDistribute(BigDecimal amountToDistribute) { this.amountToDistribute = amountToDistribute; }
    public LocalDate getPayoutDate() { return payoutDate; }
    public void setPayoutDate(LocalDate payoutDate) { this.payoutDate = payoutDate; }
    public String getRunNotes() { return runNotes; }
    public void setRunNotes(String runNotes) { this.runNotes = runNotes; }
    public String getLookup() { return lookup; }
    public void setLookup(String lookup) { this.lookup = lookup; }
    public MemberView getFound() { return found; }
    public MembershipRole getAddRole() { return addRole; }
    public void setAddRole(MembershipRole addRole) { this.addRole = addRole; }
    public LocalDate getAddJoined() { return addJoined; }
    public void setAddJoined(LocalDate addJoined) { this.addJoined = addJoined; }
    public String getRegName() { return regName; }
    public void setRegName(String regName) { this.regName = regName; }
    public String getRegNationalId() { return regNationalId; }
    public void setRegNationalId(String regNationalId) { this.regNationalId = regNationalId; }
    public String getRegPhone() { return regPhone; }
    public void setRegPhone(String regPhone) { this.regPhone = regPhone; }
    public String getRegEmail() { return regEmail; }
    public void setRegEmail(String regEmail) { this.regEmail = regEmail; }
    public NotificationChannel getRegChannel() { return regChannel; }
    public void setRegChannel(NotificationChannel regChannel) { this.regChannel = regChannel; }
    public boolean isRegConsent() { return regConsent; }
    public void setRegConsent(boolean regConsent) { this.regConsent = regConsent; }
    public String getSetName() { return setName; }
    public void setSetName(String setName) { this.setName = setName; }
    public String getSetDescription() { return setDescription; }
    public void setSetDescription(String setDescription) { this.setDescription = setDescription; }
    public GroupType getSetType() { return setType; }
    public void setSetType(GroupType setType) { this.setType = setType; }
    public BigDecimal getSetAmount() { return setAmount; }
    public void setSetAmount(BigDecimal setAmount) { this.setAmount = setAmount; }
    public ContributionFrequency getSetFrequency() { return setFrequency; }
    public void setSetFrequency(ContributionFrequency setFrequency) { this.setFrequency = setFrequency; }
    public LocalDate getSetStart() { return setStart; }
    public void setSetStart(LocalDate setStart) { this.setStart = setStart; }
    public BigDecimal getSetApprovalThreshold() { return setApprovalThreshold; }
    public void setSetApprovalThreshold(BigDecimal setApprovalThreshold) { this.setApprovalThreshold = setApprovalThreshold; }
    public Integer getSetCompletion() { return setCompletion; }
    public void setSetCompletion(Integer setCompletion) { this.setCompletion = setCompletion; }
    public BigDecimal getSetBenefit() { return setBenefit; }
    public void setSetBenefit(BigDecimal setBenefit) { this.setBenefit = setBenefit; }
    public String getStatusReason() { return statusReason; }
    public void setStatusReason(String statusReason) { this.statusReason = statusReason; }
}
