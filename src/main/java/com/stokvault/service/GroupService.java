package com.stokvault.service;

import com.stokvault.audit.AuditService;
import com.stokvault.domain.AuditAction;
import com.stokvault.domain.GroupStatus;
import com.stokvault.domain.MembershipRole;
import com.stokvault.domain.MembershipStatus;
import com.stokvault.domain.PayoutStatus;
import com.stokvault.dto.GroupRequest;
import com.stokvault.dto.GroupView;
import com.stokvault.entity.StokvelGroup;
import com.stokvault.exception.BusinessRuleException;
import com.stokvault.exception.ResourceNotFoundException;
import com.stokvault.security.AccessControl;
import com.stokvault.security.Roles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Registering and configuring stokvel groups, and their DRAFT / ACTIVE / SUSPENDED / CLOSED
 * lifecycle (SDD 4.1).
 */
@Stateless
@RolesAllowed(Roles.MEMBER)
public class GroupService {

    static final BigDecimal DEFAULT_APPROVAL_THRESHOLD = new BigDecimal("5000.00");

    @PersistenceContext(unitName = "StokVaultPU")
    private EntityManager em;

    @Inject
    private AccessControl access;

    @Inject
    private AuditService audit;

    /** Admins see every group; everyone else sees the groups they belong (or belonged) to. */
    public List<GroupView> list() {
        List<StokvelGroup> groups = access.isAdmin()
                ? em.createQuery("SELECT g FROM StokvelGroup g ORDER BY g.name", StokvelGroup.class).getResultList()
                : em.createQuery("""
                                SELECT DISTINCT ms.group FROM Membership ms
                                WHERE ms.member.id = :me ORDER BY ms.group.name""", StokvelGroup.class)
                        .setParameter("me", access.currentMemberId().orElse(null))
                        .getResultList();
        return groups.stream().map(this::view).toList();
    }

    public GroupView view(StokvelGroup group) {
        return GroupView.from(group, access.roleIn(group).orElse(null));
    }

    /** Loads a group the caller may see, or 404. */
    public StokvelGroup find(UUID id) {
        StokvelGroup group = em.find(StokvelGroup.class, id);
        if (group == null) {
            throw new ResourceNotFoundException("Group " + id + " not found");
        }
        access.requireCanView(group);
        return group;
    }

    /** Loads a group and locks its row until the transaction ends, for balance-changing work. */
    public StokvelGroup findForUpdate(UUID id) {
        StokvelGroup group = find(id);
        em.lock(group, LockModeType.PESSIMISTIC_WRITE);
        em.refresh(group);
        return group;
    }

    public void requireActive(StokvelGroup group) {
        if (group.getStatus() != GroupStatus.ACTIVE) {
            throw new BusinessRuleException(group.getName() + " is " + group.getStatus().name().toLowerCase()
                    + "; this needs an active group");
        }
    }

    /** Register / Configure Group (SDD use case, Coop Office Admin). New groups start as DRAFT. */
    @RolesAllowed(Roles.ADMIN)
    public StokvelGroup create(@Valid @NotNull GroupRequest request) {
        requireUniqueName(request.name(), null);
        StokvelGroup group = new StokvelGroup();
        apply(group, request);
        group.setStatus(GroupStatus.DRAFT);
        em.persist(group);
        audit.record(group, AuditAction.GROUP_CREATED, "StokvelGroup", group.getId(), describe(group));
        return group;
    }

    /** Admins and the group's treasurer can change the configuration; the type is fixed once active. */
    public StokvelGroup update(UUID id, @Valid @NotNull GroupRequest request) {
        StokvelGroup group = find(id);
        access.requireRoleOrAdmin(group, MembershipRole.TREASURER);
        if (group.getStatus() == GroupStatus.CLOSED) {
            throw new BusinessRuleException(group.getName() + " is closed");
        }
        if (group.getStatus() != GroupStatus.DRAFT && request.type() != group.getType()) {
            throw new BusinessRuleException("The stokvel type can only be changed while the group is a draft");
        }
        requireUniqueName(request.name(), group.getId());
        Map<String, Object> before = describe(group);
        apply(group, request);
        Map<String, Object> after = describe(group);
        Map<String, Object> changes = new LinkedHashMap<>();
        after.forEach((field, value) -> {
            if (!Objects.equals(before.get(field), value)) {
                changes.put(field, Map.of("from", String.valueOf(before.get(field)), "to", String.valueOf(value)));
            }
        });
        if (!changes.isEmpty()) {
            audit.record(group, AuditAction.GROUP_UPDATED, "StokvelGroup", group.getId(), changes);
        }
        return group;
    }

    /** DRAFT -> ACTIVE. Needs an active treasurer. */
    public StokvelGroup activate(UUID id) {
        StokvelGroup group = find(id);
        access.requireRoleOrAdmin(group, MembershipRole.TREASURER);
        if (group.getStatus() != GroupStatus.DRAFT) {
            throw new BusinessRuleException(group.getName() + " is already " + group.getStatus().name().toLowerCase());
        }
        long treasurers = em.createQuery("""
                        SELECT COUNT(ms) FROM Membership ms
                        WHERE ms.group = :group AND ms.role = :treasurer AND ms.status = :active""", Long.class)
                .setParameter("group", group)
                .setParameter("treasurer", MembershipRole.TREASURER)
                .setParameter("active", MembershipStatus.ACTIVE)
                .getSingleResult();
        if (treasurers == 0) {
            throw new BusinessRuleException("Appoint a treasurer before activating " + group.getName());
        }
        return changeStatus(group, GroupStatus.ACTIVE, null);
    }

    /** ACTIVE -> SUSPENDED (e.g. during a dispute). The committee or an admin can do this. */
    public StokvelGroup suspend(UUID id, String reason) {
        StokvelGroup group = find(id);
        access.requireRoleOrAdmin(group, MembershipRole.COMMITTEE);
        if (group.getStatus() != GroupStatus.ACTIVE) {
            throw new BusinessRuleException("Only an active group can be suspended");
        }
        return changeStatus(group, GroupStatus.SUSPENDED, reason);
    }

    /** SUSPENDED -> ACTIVE. Admin only, so a suspension can't be lifted by the people involved. */
    @RolesAllowed(Roles.ADMIN)
    public StokvelGroup resume(UUID id) {
        StokvelGroup group = find(id);
        if (group.getStatus() != GroupStatus.SUSPENDED) {
            throw new BusinessRuleException("Only a suspended group can be resumed");
        }
        return changeStatus(group, GroupStatus.ACTIVE, null);
    }

    /** Anything but CLOSED -> CLOSED, once no payouts are outstanding. Admin only. */
    @RolesAllowed(Roles.ADMIN)
    public StokvelGroup close(UUID id, String reason) {
        StokvelGroup group = find(id);
        if (group.getStatus() == GroupStatus.CLOSED) {
            throw new BusinessRuleException(group.getName() + " is already closed");
        }
        long openPayouts = em.createQuery("SELECT COUNT(p) FROM Payout p WHERE p.group = :group AND p.status IN :open", Long.class)
                .setParameter("group", group)
                .setParameter("open", List.of(PayoutStatus.SCHEDULED, PayoutStatus.PENDING_APPROVAL, PayoutStatus.CONFIRMED))
                .getSingleResult();
        if (openPayouts > 0) {
            throw new BusinessRuleException(group.getName() + " has " + openPayouts + " payout(s) in progress. Pay or cancel them first");
        }
        return changeStatus(group, GroupStatus.CLOSED, reason);
    }

    private StokvelGroup changeStatus(StokvelGroup group, GroupStatus to, String reason) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("from", group.getStatus());
        details.put("to", to);
        if (reason != null && !reason.isBlank()) {
            details.put("reason", reason.trim());
        }
        group.setStatus(to);
        audit.record(group, AuditAction.GROUP_STATUS_CHANGED, "StokvelGroup", group.getId(), details);
        return group;
    }

    private void apply(StokvelGroup group, GroupRequest request) {
        group.setName(request.name().trim());
        group.setDescription(request.description() == null || request.description().isBlank() ? null : request.description().trim());
        group.setType(request.type());
        group.setContributionAmount(request.contributionAmount());
        group.setFrequency(request.frequency());
        group.setStartDate(request.startDate());
        group.setApprovalThreshold(request.approvalThreshold() == null ? DEFAULT_APPROVAL_THRESHOLD : request.approvalThreshold());
        group.setCompletionThreshold(request.completionThreshold() == null ? 100 : request.completionThreshold());
        group.setBenefitAmount(request.benefitAmount());
    }

    private void requireUniqueName(String name, UUID exceptId) {
        em.createQuery("SELECT g FROM StokvelGroup g WHERE LOWER(g.name) = LOWER(:name)", StokvelGroup.class)
                .setParameter("name", name.trim())
                .getResultStream()
                .filter(g -> !g.getId().equals(exceptId))
                .findFirst()
                .ifPresent(g -> {
                    throw new BusinessRuleException("A group called " + g.getName() + " already exists");
                });
    }

    private static Map<String, Object> describe(StokvelGroup g) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("name", g.getName());
        d.put("type", g.getType());
        d.put("contributionAmount", g.getContributionAmount());
        d.put("frequency", g.getFrequency());
        d.put("startDate", String.valueOf(g.getStartDate()));
        d.put("approvalThreshold", g.getApprovalThreshold());
        d.put("completionThreshold", g.getCompletionThreshold());
        d.put("benefitAmount", g.getBenefitAmount() == null ? "none" : g.getBenefitAmount());
        return d;
    }
}
