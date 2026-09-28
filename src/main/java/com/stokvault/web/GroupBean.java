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
import com.stokvault.dto.AuditEntryView;
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
 * One group's workspace (app/group.xhtml?id=...&tab=...). What each person sees and can do
 * depends on their role in the group; the services enforce the same rules again, so hiding a
 * button is only for convenience, never the security boundary.
 */
@Named
@ViewScoped
public class GroupBean implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Set<String> TABS = Set.of("overview", "contributions", "payouts", "members", "audit", "settings");

    @Inject private GroupService groups;
    @Inject private MembershipService memberships;
    @Inject private CycleService cycles;
    @Inject private ContributionService contributions;
    @Inject private PayoutService payouts;
    @Inject private ReportService reports;
    @Inject private AuditService audit;
    @Inject private MemberService members;
    @Inject private UserSession user;

    // URL parameters (f:viewParam)
    private UUID id;
    private String tab = "overview";

    // Loaded data
    private GroupSummary summary;
    private CycleGrid grid;
    private List<CycleView> cycleList = List.of();
    private List<ContributionView> contributionList = List.of();
    private List<PayoutView> payoutList = List.of();
    private List<MembershipView> memberList = List.of();
    private List<AuditEntryView> auditList = List.of();
    private HashChain.Verification chain;

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
        if (!TABS.contains(tab)) {
            tab = "overview";
        }
        if (id == null) {
            FacesContext.getCurrentInstance().getExternalContext().redirect("index.xhtml");
            return;
        }
        try {
            reload();
        } catch (RuntimeException e) {
            Ui.error(Errors.message(e));
            FacesContext.getCurrentInstance().getExternalContext().redirect("index.xhtml");
        }
    }

    private void reload() {
        summary = reports.summary(id);
        grid = null;
        if (tab.equals("contributions") || tab.equals("overview")) {
            contributionList = contributions.list(id, null, null, null).stream().map(ContributionView::from).toList();
            if (isOfficer() && summary.currentCycle() != null) {
                grid = cycles.grid(id, summary.currentCycle().id());
            }
            cycleList = cycles.list(id);
            if (amount == null && summary.currentCycle() != null) {
                amount = summary.currentCycle().amountDue();
            }
        }
        if (tab.equals("payouts")) {
            payoutList = payouts.list(id, null).stream().map(PayoutView::from).toList();
        }
        if (tab.equals("payouts") || tab.equals("members") || tab.equals("contributions")) {
            memberList = memberships.list(id, tab.equals("members")).stream().map(MembershipView::from).toList();
            memberList.forEach(m -> {
                roleEdits.putIfAbsent(m.memberId(), m.role().name());
                positionEdits.putIfAbsent(m.memberId(), String.valueOf(m.payoutPosition()));
            });
        }
        if (tab.equals("audit") && isOfficer()) {
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

    // ---- contributions ----

    public void recordContribution() {
        UUID payer = isTreasurer() ? payerId : null;
        act(() -> {
            ContributionService.Recorded recorded = contributions.record(id,
                    new ContributionRequest(payer, null, amount, reference, method, paidOn));
            String status = Ui.label(recorded.contribution().getVerificationStatus()).toLowerCase();
            Ui.info(recorded.created()
                    ? "Contribution recorded (" + status + ")"
                    + (recorded.contribution().getReviewNote() == null ? "" : ": " + recorded.contribution().getReviewNote())
                    : "That payment (same member, cycle and reference) was already recorded; nothing was duplicated");
            reference = null;
        }, null);
    }

    public void verify(UUID contributionId) {
        act(() -> contributions.verify(id, contributionId, new VerificationDecision(VerificationStatus.VERIFIED, notes.get(contributionId))),
                "Contribution verified");
    }

    public void reject(UUID contributionId) {
        act(() -> contributions.verify(id, contributionId, new VerificationDecision(VerificationStatus.REJECTED, notes.get(contributionId))),
                "Contribution rejected");
    }

    public List<ContributionView> getUnresolved() {
        return contributionList.stream().filter(c -> c.verificationStatus().isUnresolved()).toList();
    }

    public boolean canVerify(ContributionView c) {
        return (isTreasurer() || isCommittee()) && c.verificationStatus().isUnresolved()
                && !c.memberId().equals(user.getMe().id());
    }

    // ---- cycles ----

    public void openCycle() {
        act(() -> cycles.open(id, newCycleDue), "New contribution cycle opened");
        newCycleDue = null;
    }

    public void closeCycle(UUID cycleId) {
        act(() -> cycles.close(id, cycleId), "Cycle closed");
    }

    public void reconcileCycle(UUID cycleId) {
        act(() -> cycles.reconcile(id, cycleId), "Cycle reconciled");
    }

    public boolean isCycleOpen() {
        return summary.currentCycle() != null && summary.currentCycle().status() == CycleStatus.OPEN;
    }

    // ---- payouts ----

    public void runPayout() {
        act(() -> {
            int count = payouts.run(id, new PayoutRunRequest(null, beneficiaryId, amountToDistribute, payoutDate, runNotes)).size();
            Ui.info(count + " payout(s) scheduled. The automated eligibility check is running; refresh in a moment to see the result.");
            runNotes = null;
            beneficiaryId = null;
        }, null);
    }

    public void checkEligibility() {
        act(() -> payouts.startEligibilityCheck(id), "Eligibility check started in the background; refresh in a moment");
    }

    public void confirm(UUID payoutId) {
        act(() -> {
            PayoutStatus result = payouts.confirm(id, payoutId).getStatus();
            Ui.info(result == PayoutStatus.PENDING_APPROVAL
                    ? "Above the approval threshold: sent to the committee for four-eyes approval"
                    : "Payout confirmed; it can now be paid");
        }, null);
    }

    public void approve(UUID payoutId) {
        act(() -> payouts.approve(id, payoutId), "Payout approved");
    }

    public void override(UUID payoutId) {
        String reason = notes.get(payoutId);
        if (reason == null || reason.isBlank()) {
            Ui.error("Give a reason for overriding the eligibility check");
            return;
        }
        act(() -> payouts.override(id, payoutId, reason), "Eligibility check overridden; the reason is in the audit trail");
    }

    public void pay(UUID payoutId) {
        act(() -> payouts.pay(id, payoutId), "Payout marked as paid; the member has been notified");
    }

    public void cancel(UUID payoutId) {
        act(() -> payouts.cancel(id, payoutId, notes.get(payoutId)), "Payout cancelled");
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
