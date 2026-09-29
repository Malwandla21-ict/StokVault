package com.stokvault.web;

import com.stokvault.dto.GroupView;
import com.stokvault.dto.PayoutView;
import com.stokvault.service.GroupService;
import com.stokvault.service.PayoutService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The committee's approval queue (SDD 6.1 Committee dashboard): high-value payouts waiting for a
 * second person's approval, across all the committee member's groups.
 */
@Named
@ViewScoped
public class ApprovalsBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    private PayoutService payouts;

    @Inject
    private UserSession user;

    @Inject
    private GroupService groups;

    private List<PayoutView> queue = List.of();
    private final Map<UUID, String> reasons = new HashMap<>();
    private Map<UUID, String> groupNames = Map.of();

    @PostConstruct
    void load() {
        queue = user.isCommittee() ? payouts.approvalQueue().stream().map(PayoutView::from).toList() : List.of();
        groupNames = groups.list().stream().collect(java.util.stream.Collectors.toMap(GroupView::id, GroupView::name));
    }

    public void approve(PayoutView payout) {
        Ui.attempt(() -> payouts.approve(payout.groupId(), payout.id()), "Approved ✓ " + Ui.rand(payout.amount()) + " to " + payout.memberName() + ". The treasurer can now pay it out.");
        load();
    }

    public void decline(PayoutView payout) {
        String reason = reasons.get(payout.id());
        if (reason == null || reason.isBlank()) {
            Ui.error("Say why you are declining. It is kept in the records.");
            return;
        }
        Ui.attempt(() -> payouts.cancel(payout.groupId(), payout.id(), "Declined by committee: " + reason), "Payout declined");
        load();
    }

    /** The stokvel a payout belongs to, by name. */
    public String groupName(UUID groupId) {
        return groupNames.getOrDefault(groupId, "");
    }

    public boolean isOwn(PayoutView payout) {
        return payout.memberId().equals(user.getMe().id());
    }

    public List<PayoutView> getQueue() {
        return queue;
    }

    public Map<UUID, String> getReasons() {
        return reasons;
    }
}
