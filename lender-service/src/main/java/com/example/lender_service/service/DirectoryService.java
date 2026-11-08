package com.example.lender_service.service;

import static com.example.lender_service.service.Checks.*;

import com.example.lender_service.dto.*;
import com.example.lender_service.model.*;
import com.example.lender_service.repository.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class DirectoryService {
  private final OrganizationRepository organizations;
  private final UserRepository users;
  private final RoleRepository roles;
  private final LenderAccountRepository accounts;
  private final BorrowerConnectionRepository connections;
  private final TenantService tenants;
  private final EventService events;
  private final Clock clock;

  public Organization create(OrganizationRequest request) {
    var o = new Organization();
    o.setName(request.name());
    o.setExternalId(request.externalId());
    o.setPublicReferenceId(request.publicReferenceId());
    o.setCreatedAt(clock.instant());
    organizations.saveAndFlush(o);
    var a = new LenderAccount();
    a.setOrganizationId(o.getId());
    a.setFeeReceived(BigDecimal.ZERO);
    a.setRebatePaid(BigDecimal.ZERO);
    a.setCashCollateralHeld(BigDecimal.ZERO);
    accounts.save(a);
    events.local(
        o.getId(), "ORGANIZATION_CREATED", o.getId(), Map.of("externalId", o.getExternalId()));
    return o;
  }

  public User member(long org, MemberRequest r) {
    tenants.lock(org);
    if (users.countByOrganizationId(org) == 0)
      require("ADMIN".equals(r.role()), "First user must be ADMIN");
    var u = new User();
    u.setOrganizationId(org);
    u.setName(r.name());
    u.setEmail(r.email().toLowerCase(Locale.ROOT));
    u.setRoleId(found(roles.findByRoleName(r.role())).getId());
    u.setActive(true);
    u.setCreatedAt(clock.instant());
    users.saveAndFlush(u);
    events.audit(org, "USER_ADDED", u.getId().toString());
    return u;
  }

  public User updateMember(long org, long id, MemberUpdateRequest r) {
    tenants.lock(org);
    var u = found(users.findByIdAndOrganizationId(id, org));
    long admin = found(roles.findByRoleName("ADMIN")).getId();
    if (u.getRoleId() == admin && u.getActive() && (!r.active() || !r.role().equals("ADMIN")))
      require(
          users.countByOrganizationIdAndRoleIdAndActiveTrue(org, admin) > 1,
          "Cannot remove the last active administrator");
    u.setRoleId(found(roles.findByRoleName(r.role())).getId());
    u.setActive(r.active());
    events.audit(org, "USER_UPDATED", String.valueOf(id));
    return u;
  }

  public BorrowerConnection connect(long org, BorrowerConnectionRequest r) {
    var lender = tenants.lock(org);
    var c = new BorrowerConnection();
    c.setOrganizationId(org);
    c.setExternalId(r.externalId());
    c.setBorrowerOrganizationId(r.borrowerOrganizationId());
    c.setName(r.name());
    c.setActive(true);
    c.setCreatedAt(clock.instant());
    connections.saveAndFlush(c);
    events.confirm(
        c,
        "LENDER_REFERENCE_UPDATED",
        "LENDER_REFERENCE_UPDATED",
        lender.getPublicReferenceId(),
        Map.of("name", lender.getName(), "externalId", lender.getExternalId()));
    return c;
  }
}
