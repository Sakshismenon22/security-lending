package com.example.lender_service.service;

import static com.example.lender_service.service.Checks.*;

import com.example.lender_service.model.*;
import com.example.lender_service.repository.*;
import java.time.*;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AgreementService {
  private final MslaRepository agreements;
  private final BorrowerConnectionRepository connections;
  private final TenantService tenants;
  private final EventService events;
  private final Clock clock;

  public Msla active(long org, long id) {
    var a = found(agreements.findByIdAndOrganizationId(id, org));
    LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata")));
    require(
        "ACTIVE".equals(a.getStatus())
            && !today.isBefore(a.getEffectiveDate())
            && !today.isAfter(a.getExpiryDate()),
        "MSLA is not active within its effective dates");
    tenants.connection(org, a.getConnectionId());
    return a;
  }

  public Msla decide(long org, long id, boolean approve, String reason) {
    tenants.lock(org);
    var a = found(agreements.findByIdAndOrganizationId(id, org));
    String status = approve ? "ACTIVE" : "REJECTED";
    if (status.equals(a.getStatus())) return a;
    require("PROPOSED".equals(a.getStatus()), "Agreement already decided");
    if (approve)
      require(
          !a.getExpiryDate().isBefore(LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata")))),
          "Agreement has expired");
    a.setStatus(status);
    events.confirm(
        found(connections.findById(a.getConnectionId())),
        approve ? "AGREEMENT_ACCEPTED" : "AGREEMENT_REJECTED",
        "msla",
        a.getBorrowerMslaId(),
        Map.of("reason", reason));
    events.local(org, "AGREEMENT_DECIDED", id, Map.of("status", status, "agreement", a));
    return a;
  }
}
