package com.example.lender_service.service;

import static com.example.lender_service.service.Checks.*;

import com.example.lender_service.dto.AgreementOfferRequest;
import com.example.lender_service.model.*;
import com.example.lender_service.repository.*;
import java.util.HashSet;
import org.springframework.data.domain.Pageable;
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
  private final AssetRepository assets;
  private final SecurityMasterRepository securities;
  private final CollateralHaircutRepository rules;
  private final TenantService tenants;
  private final EventService events;
  private final JsonService json;
  private final Clock clock;
  private final OutboxEventRepository outbox;
  @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;

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

  /** Creates lender-authored terms and transmits them to exactly one connected borrower. */
  public Msla offer(long org, AgreementOfferRequest request) {
    var lender = tenants.lock(org);
    var connection = tenants.connection(org, request.connectionId());
    require(
        !request.expiryDate().isBefore(request.effectiveDate())
            && (request.dayBasis() == 360 || request.dayBasis() == 365),
        "Invalid agreement dates/day basis");

    var agreement = new Msla();
    agreement.setOrganizationId(org);
    agreement.setConnectionId(connection.getId());
    agreement.setNumber(request.number());
    agreement.setStatus("OFFERED");
    agreement.setEffectiveDate(request.effectiveDate());
    agreement.setExpiryDate(request.expiryDate());
    agreement.setCreditLimit(request.creditLimit());
    agreement.setFeeRate(request.feeRate());
    agreement.setRebateRate(request.rebateRate());
    agreement.setCoverageRatio(request.coverageRatio());
    agreement.setBillingFrequency(request.billingFrequency());
    agreement.setRebateSettlementFrequency(request.rebateSettlementFrequency());
    agreement.setDayBasis(request.dayBasis());
    agreement.setRecallAllowed(request.recallAllowed());
    agreement.setMarginHours(request.marginHours());
    agreement.setRecallHours(request.recallHours());
    agreement.setTerms(json.write(request));
    agreement.setCreatedAt(clock.instant());
    agreements.saveAndFlush(agreement);

    var unique = new HashSet<String>();
    for (var rule : request.collateralRules()) {
      boolean cash = "CASH".equals(rule.assetType());
      require(
          cash
              ? rule.securityId() == null && rule.haircut().signum() == 0
              : rule.securityId() != null,
          "Invalid collateral eligibility rule");
      require(unique.add(rule.assetType() + ":" + rule.securityId()), "Duplicate collateral rule");
      if (!cash) found(securities.findById(rule.securityId()));
      var haircut = new CollateralHaircut();
      haircut.setOrganizationId(org);
      haircut.setMslaId(agreement.getId());
      haircut.setAssetId(found(assets.findByAssetName(rule.assetType())).getId());
      haircut.setAssetType(rule.assetType());
      haircut.setSecurityId(rule.securityId());
      haircut.setHaircut(rule.haircut());
      rules.save(haircut);
    }
    events.offer(connection, lender, agreement, request);
    events.local(org, "AGREEMENT_OFFER_SENT", agreement.getId(), Map.of("agreement", agreement));
    return agreement;
  }

  /** Re-deliver the same immutable offer snapshot, retaining its domain ID and version. */
  public Map<String,Object> resend(long org, long id) {
    var lender = tenants.lock(org);
    var agreement = found(agreements.findByIdAndOrganizationId(id, org));
    require("OFFERED".equals(agreement.getStatus()), "Only an unanswered offer can be re-delivered");
    var connection = tenants.connection(org, agreement.getConnectionId());
    var pending = outbox.findByOrganizationId(org, Pageable.unpaged()).getContent();
    for (var event : pending) {
      var payload = json.tree(event.getPayload());
      if ("AGREEMENT_OFFERED".equals(payload.path("type").asText()) && payload.path("referenceId").asLong() == id
          && !event.getPublished() && "PENDING".equals(event.getStatus())) {
        return Map.of("agreementId", id, "status", "QUEUED", "alreadyQueued", true);
      }
    }
    for (var event : pending) {
      var payload = json.tree(event.getPayload());
      boolean offer = "AGREEMENT_OFFERED".equals(payload.path("type").asText()) && payload.path("referenceId").asLong() == id;
      boolean reference = "LENDER_REFERENCE_UPDATED".equals(payload.path("type").asText()) && payload.path("organizationId").asLong() == connection.getBorrowerOrganizationId() && payload.path("referenceId").asLong() == lender.getPublicReferenceId();
      if ((offer || reference) && "FAILED".equals(event.getStatus())) {
        event.setStatus("CLOSED");
        em.createQuery("update DeliveryFailure f set f.status='CLOSED' where f.organizationId=:org and f.eventId=:event and f.status in ('FAILED','RETRYING')")
          .setParameter("org", org).setParameter("event", event.getEventId()).executeUpdate();
      }
    }
    events.confirm(connection, "LENDER_REFERENCE_UPDATED", "LENDER_REFERENCE_UPDATED", lender.getPublicReferenceId(), Map.of("name", lender.getName(), "externalId", lender.getExternalId()));
    events.offer(connection, lender, agreement, json.parse(agreement.getTerms(), AgreementOfferRequest.class));
    events.audit(org, "AGREEMENT_DELIVERY_RETRIED", String.valueOf(id));
    return Map.of("agreementId", id, "status", "QUEUED", "alreadyQueued", false);
  }

  /** Applies the borrower's response to a lender-originated offer. */
  public Msla recordBorrowerDecision(
      long org, BorrowerConnection connection, long lenderAgreementId, long borrowerAgreementId,
      boolean accepted, String reason) {
    var agreement =
        found(agreements.findByIdAndOrganizationIdAndConnectionId(lenderAgreementId, org, connection.getId()));
    require("OFFERED".equals(agreement.getStatus()), "Agreement already decided");
    if (accepted)
      require(
          !agreement.getExpiryDate().isBefore(LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata")))),
          "Agreement has expired");
    agreement.setBorrowerMslaId(borrowerAgreementId);
    agreement.setStatus(accepted ? "ACTIVE" : "REJECTED");
    events.confirm(
        connection,
        accepted ? "AGREEMENT_ACCEPTED" : "AGREEMENT_REJECTED",
        "msla",
        borrowerAgreementId,
        Map.of("reason", reason == null ? "" : reason, "lenderAgreementId", agreement.getId()));
    events.local(
        org,
        "AGREEMENT_BORROWER_DECISION_RECEIVED",
        agreement.getId(),
        Map.of("status", agreement.getStatus(), "borrowerAgreementId", borrowerAgreementId));
    return agreement;
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
