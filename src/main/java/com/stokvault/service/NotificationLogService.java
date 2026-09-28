package com.stokvault.service;

import com.stokvault.domain.MembershipRole;
import com.stokvault.domain.NotificationStatus;
import com.stokvault.entity.NotificationEvent;
import com.stokvault.entity.StokvelGroup;
import com.stokvault.security.AccessControl;
import com.stokvault.security.Roles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.List;
import java.util.UUID;

/**
 * Reading the notification log: admins see everything (including the dead-letter FAILED ones
 * needing manual review), group officers see their group's notifications, members their own.
 */
@Stateless
@RolesAllowed(Roles.MEMBER)
public class NotificationLogService {

    @PersistenceContext(unitName = "StokVaultPU")
    private EntityManager em;

    @Inject
    private AccessControl access;

    @Inject
    private GroupService groups;

    @RolesAllowed(Roles.ADMIN)
    public List<NotificationEvent> all(NotificationStatus status) {
        var query = em.createQuery("SELECT n FROM NotificationEvent n"
                + (status == null ? "" : " WHERE n.status = :status") + " ORDER BY n.createdAt DESC", NotificationEvent.class);
        if (status != null) {
            query.setParameter("status", status);
        }
        return query.setMaxResults(300).getResultList();
    }

    public List<NotificationEvent> forGroup(UUID groupId) {
        StokvelGroup group = groups.find(groupId);
        access.requireRoleOrAdmin(group, MembershipRole.TREASURER, MembershipRole.COMMITTEE);
        return em.createQuery("SELECT n FROM NotificationEvent n WHERE n.group = :group ORDER BY n.createdAt DESC", NotificationEvent.class)
                .setParameter("group", group)
                .setMaxResults(300)
                .getResultList();
    }

    public List<NotificationEvent> mine() {
        return em.createQuery("SELECT n FROM NotificationEvent n WHERE n.member.id = :me ORDER BY n.createdAt DESC", NotificationEvent.class)
                .setParameter("me", access.currentMember().getId())
                .setMaxResults(100)
                .getResultList();
    }
}
