package com.example.borrower_service.service;

import static com.example.borrower_service.repository.BorrowerRepository.*;

import com.example.borrower_service.dto.*;
import com.example.borrower_service.model.*;
import com.example.borrower_service.repository.BorrowerRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
@Transactional
public class DirectoryService {
    private final BorrowerRepository db;
    private final EventWriter events;

    public DirectoryService(BorrowerRepository db, EventWriter events) {
        this.db = db;
        this.events = events;
    }

    public Map<String, Object> organization(OrganizationRequest r) {
        long id =
                db.create(
                        Organization::new,
                        entity -> {
                            entity.setName(r.name());
                            entity.setRegistrationNumber(r.registrationNumber());
                            entity.setCurrency(r.currency());
                        });
        db.persist(
                TraderAccount::new,
                entity -> {
                    entity.setOrganizationId(id);
                });
        events.write(id, "ORGANIZATION_CREATED", id, Map.of("name", r.name()));
        return db.row(Organization.class, id);
    }

    public Map<String, Object> member(long org, MemberRequest r) {
        db.lock(org);
        if (db.rows(User.class, eq("organizationId", org)).isEmpty())
            require(
                    "ADMIN".equals(r.role()),
                    "The first organization user must be an administrator");
        long roleId = n(db.matchingRow(Role.class, eq("roleName", r.role())), "id");
        long id =
                db.create(
                        User::new,
                        entity -> {
                            entity.setOrganizationId(org);
                            entity.setName(r.name());
                            entity.setEmail(r.email().toLowerCase(Locale.ROOT));
                            entity.setRoleId(roleId);
                        });
        events.write(org, "MEMBER_ADDED", id, Map.of("role", r.role()));
        return db.owned(User.class, org, id);
    }

    public Map<String, Object> updateMember(long org, long id, MemberUpdateRequest r) {
        db.lock(org);
        var old = db.owned(User.class, org, id);
        long admin = n(db.matchingRow(Role.class, eq("roleName", "ADMIN")), "id");
        if (n(old, "role_id") == admin
                && Boolean.TRUE.equals(old.get("active"))
                && (!r.active() || !"ADMIN".equals(r.role())))
            require(
                    db.rows(
                                            User.class,
                                            eq("organizationId", org),
                                            eq("roleId", admin),
                                            eq("active", true))
                                    .size()
                            > 1,
                    "Cannot remove the last active administrator");
        long roleId = n(db.matchingRow(Role.class, eq("roleName", r.role())), "id");
        db.edit(
                User.class,
                id,
                entity -> {
                    entity.setRoleId(roleId);
                    entity.setActive(r.active());
                });
        events.write(org, "MEMBER_UPDATED", id, Map.of("role", r.role(), "active", r.active()));
        return db.owned(User.class, org, id);
    }

    public void changeCash(long org, BigDecimal delta, String type, String reference) {
        var account = db.matchingRow(TraderAccount.class, eq("organizationId", org));
        var balance = amount(money(account, "available").add(delta));
        require(balance.signum() >= 0, "Insufficient available cash");
        db.editMatching(
                TraderAccount.class,
                entity -> {
                    entity.setAvailable(balance);
                },
                eq("organizationId", org));
        db.create(
                TraderAccountTransaction::new,
                entity -> {
                    entity.setOrganizationId(org);
                    entity.setType(type);
                    entity.setAmount(delta);
                    entity.setBalanceAfter(balance);
                    entity.setReference(reference);
                });
        events.write(org, type, reference, Map.of("amount", delta, "available", balance));
    }
}
