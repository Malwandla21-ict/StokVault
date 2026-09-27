package com.stokvault.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A stokvel member. Each Member object corresponds to one row in the "members" table.
 */
// @Entity: tells JPA this class is stored in the database
@Entity
// @Table: the table name. Without it JPA would use the class name ("member").
@Table(name = "members")
// @NamedQuery: a reusable JPQL query. JPQL queries entities/fields, not tables/columns.
@NamedQuery(name = "Member.findAll", query = "SELECT m FROM Member m ORDER BY m.name")
public class Member {

    // @Id: the primary key
    @Id
    // @SequenceGenerator: a PostgreSQL sequence (an auto-incrementing counter) that
    // JPA creates alongside the table. allocationSize = 1 means ids go 1, 2, 3...
    // (IDENTITY is avoided: Payara's EclipseLink generates invalid "BIGINT SERIAL" SQL for it.)
    @SequenceGenerator(name = "member_seq", sequenceName = "members_id_seq", allocationSize = 1)
    // @GeneratedValue: take the next id from that sequence when a new member is saved
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "member_seq")
    private Long id;

    // @NotBlank / @Size / @Email are Bean Validation rules. They are checked
    // automatically when a request body is marked @Valid (see MemberResource)
    // and again by JPA before the row is saved.
    @NotBlank
    @Size(max = 100)
    // @Column: column settings. nullable = false adds a NOT NULL constraint.
    @Column(nullable = false, length = 100)
    private String name;

    @NotBlank
    @Email
    @Size(max = 255)
    // unique = true adds a UNIQUE constraint, so two members can't share an email
    @Column(nullable = false, unique = true)
    private String email;

    // JPA (and JSON-B) need a public or protected no-argument constructor
    public Member() {
    }

    public Member(String name, String email) {
        this.name = name;
        this.email = email;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
