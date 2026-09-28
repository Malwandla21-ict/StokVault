package com.stokvault.service;

import com.stokvault.dto.MemberRequest;
import com.stokvault.entity.Member;
import com.stokvault.entity.Membership;
import com.stokvault.exception.BusinessRuleException;
import com.stokvault.exception.ResourceNotFoundException;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.List;
import java.util.Locale;

/**
 * Business logic for members. REST resources call this; this talks to the database.
 */
// @Stateless: makes this an EJB (Enterprise Java Bean) session bean.
//  - The server creates and pools instances; you never call "new MemberService()".
//  - It keeps no per-client state between calls, so any instance can serve any request.
//  - Every public method automatically runs in a database transaction: it commits
//    when the method returns normally and rolls back if it throws an unchecked exception.
@Stateless
public class MemberService {

    // @PersistenceContext: the server injects an EntityManager connected to the
    // "StokVaultPU" unit from persistence.xml. EntityManager is the JPA object
    // you use to save, load and delete entities.
    @PersistenceContext(unitName = "StokVaultPU")
    private EntityManager em;

    /** All members, or those whose name or email contains {@code search}. */
    public List<Member> findAll(String search) {
        if (search == null || search.isBlank()) {
            // A named query, declared with @NamedQuery on the Member entity
            return em.createNamedQuery("Member.findAll", Member.class).getResultList();
        }
        // An inline JPQL query. :pattern is a named parameter filled in by setParameter,
        // which also protects against SQL injection (never concatenate user input into JPQL).
        return em.createQuery("""
                        SELECT m FROM Member m
                        WHERE LOWER(m.name) LIKE :pattern OR LOWER(m.email) LIKE :pattern
                        ORDER BY m.name""", Member.class)
                .setParameter("pattern", "%" + search.trim().toLowerCase(Locale.ROOT) + "%")
                .getResultList();
    }

    public Member find(Long id) {
        // find() looks a row up by primary key and returns null when there isn't one
        Member member = em.find(Member.class, id);
        if (member == null) {
            throw new ResourceNotFoundException("Member " + id + " not found");
        }
        return member;
    }

    public Member create(MemberRequest request) {
        Member member = new Member();
        apply(member, request);
        em.persist(member); // INSERT happens when the transaction commits; the id is assigned now
        return member;
    }

    public Member update(Long id, MemberRequest request) {
        Member member = find(id);
        // member is "managed": JPA tracks changes to it and writes an UPDATE on commit
        apply(member, request);
        return member;
    }

    public void delete(Long id) {
        Member member = find(id);
        Long memberships = em.createQuery(
                        "SELECT COUNT(ms) FROM Membership ms WHERE ms.member = :member", Long.class)
                .setParameter("member", member)
                .getSingleResult();
        if (memberships > 0) {
            throw new BusinessRuleException(member.getName()
                    + " belongs to a stokvel, so their record is kept for the stokvel's history");
        }
        em.remove(member);
    }

    /** Every stokvel this member belongs to (or used to). */
    public List<Membership> memberships(Long id) {
        Member member = find(id);
        return em.createQuery(
                        "SELECT ms FROM Membership ms WHERE ms.member = :member ORDER BY ms.stokvel.name",
                        Membership.class)
                .setParameter("member", member)
                .getResultList();
    }

    private void apply(Member member, MemberRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        List<Member> sameEmail = em.createQuery("SELECT m FROM Member m WHERE m.email = :email", Member.class)
                .setParameter("email", email)
                .getResultList();
        if (sameEmail.stream().anyMatch(other -> !other.getId().equals(member.getId()))) {
            throw new BusinessRuleException("A member with email " + email + " already exists");
        }
        member.setName(request.name().trim());
        member.setEmail(email);
        member.setPhone(request.phone() == null || request.phone().isBlank() ? null : request.phone().trim());
    }
}
