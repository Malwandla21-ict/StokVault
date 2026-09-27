package com.stokvault.service;

import com.stokvault.entity.Member;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.List;
import java.util.Optional;

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

    public List<Member> findAll() {
        return em.createNamedQuery("Member.findAll", Member.class).getResultList();
    }

    public Optional<Member> findById(Long id) {
        // find() returns null when no row has that id
        return Optional.ofNullable(em.find(Member.class, id));
    }

    public Member create(Member member) {
        member.setId(null); // let the database choose the id
        em.persist(member); // INSERT happens when the transaction commits
        em.flush();         // ...or right now, so the generated id is available
        return member;
    }

    public Optional<Member> update(Long id, Member changes) {
        // Changing a "managed" entity is enough: JPA writes an UPDATE on commit
        return findById(id).map(existing -> {
            existing.setName(changes.getName());
            existing.setEmail(changes.getEmail());
            return existing;
        });
    }

    public boolean delete(Long id) {
        return findById(id).map(member -> {
            em.remove(member);
            return true;
        }).orElse(false);
    }
}
