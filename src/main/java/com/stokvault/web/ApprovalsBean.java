package com.stokvault.web;

import com.stokvault.dto.PayoutView;
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

    private List<PayoutView> queue = List.of();
    private final Map<UUID, String> reasons = new HashMap<>();

    @PostConstruct
    void load() {
        queue = user.isCommittee() ? payouts.approvalQueue().stream().map(PayoutView::from).toList() : List.of();
    }

    public void approve(PayoutView payout) {
        Ui.attempt(() -> payouts.approve(payout.groupId(), payout.id()), "Approved: " + Ui.rand(payout.amount()) + " to " + payout.memberName());
        load();
    }

    public void decline(PayoutView payout) {
        String reason = reasons.get(payout.id());
        if (reason == null || reason.isBlank()) {
            Ui.error("Give a reason for declining");
            return;
        }
        Ui.attempt(() -> payouts.cancel(payout.groupId(), payout.id(), "Declined by committee: " + reason), "Payout declined");
        load();
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
