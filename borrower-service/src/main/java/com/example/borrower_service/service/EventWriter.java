package com.example.borrower_service.service;

import com.example.borrower_service.model.*;
import com.example.borrower_service.repository.BorrowerRepository;

import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.*;

@Component
public class EventWriter {
    private final BorrowerRepository db;
    private final JsonMapper json = JsonMapper.builder().build();

    public EventWriter(BorrowerRepository db) {
        this.db = db;
    }

    public void write(long org, String type, Object reference, Object data) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("eventId", UUID.randomUUID().toString());
        payload.put("type", type);
        payload.put("organizationId", org);
        payload.put("referenceId", reference);
        payload.put("occurredAt", Instant.now().toString());
        payload.put("data", data);
        if (type.endsWith("_REQUESTED")
                || type.equals("AGREEMENT_PROPOSED")
                || type.equals("FEE_PAYMENT_RECEIVED")) {
            payload.put(
                    "destination",
                    Set.of("DEPOSIT_REQUESTED", "WITHDRAWAL_REQUESTED", "FEE_REQUESTED")
                                    .contains(type)
                            ? "PAYMENTS"
                            : "COUNTERPARTY");
            Long mslaId = null;
            long id = Long.parseLong(reference.toString());
            switch (type) {
                case "AGREEMENT_PROPOSED" -> mslaId = id;
                case "LOCATE_REQUESTED" ->
                        mslaId =
                                BorrowerRepository.n(
                                        db.owned(LocateRequest.class, org, id), "msla_id");
                case "RESERVATION_REQUESTED", "RESERVATION_RELEASE_REQUESTED" ->
                        mslaId =
                                BorrowerRepository.n(
                                        db.owned(Reservation.class, org, id), "msla_id");
                case "LOAN_REQUESTED", "COLLATERAL_PLEDGE_REQUESTED" ->
                        mslaId = BorrowerRepository.n(db.owned(Loan.class, org, id), "msla_id");
                case "FEE_PAYMENT_RECEIVED" -> {
                    var fee = db.owned(LendingFee.class, org, id);
                    mslaId =
                            BorrowerRepository.n(
                                    db.owned(Loan.class, org, BorrowerRepository.n(fee, "loan_id")),
                                    "msla_id");
                }
                case "RETURN_REQUESTED", "COLLATERAL_RELEASE_REQUESTED" -> {
                    Class<?> table =
                            type.equals("RETURN_REQUESTED")
                                    ? LoanReturn.class
                                    : CollateralPosition.class;
                    var row = db.owned(table, org, id);
                    mslaId =
                            BorrowerRepository.n(
                                    db.owned(Loan.class, org, BorrowerRepository.n(row, "loan_id")),
                                    "msla_id");
                }
                default -> {}
            }
            if (mslaId != null) {
                var msla = db.owned(Msla.class, org, mslaId);
                payload.put("mslaId", mslaId);
                payload.put(
                        "lenderExternalId",
                        db.row(LenderReference.class, BorrowerRepository.n(msla, "lender_id"))
                                .get("external_id"));
            }
        }
        db.create(
                AuditEvent::new,
                entity -> {
                    entity.setOrganizationId(org);
                    entity.setType(type);
                    entity.setReference(String.valueOf(reference));
                });
        if (Set.of(
                        "MARGIN_CALL_RECEIVED",
                        "RECALL_RECEIVED",
                        "DEFAULT_CONFIRMED",
                        "LOAN_SETTLEMENT_CONFIRMED",
                        "ORDER_FILLED",
                        "RETURN_SETTLEMENT_CONFIRMED")
                .contains(type))
            db.create(
                    Notification::new,
                    entity -> {
                        entity.setOrganizationId(org);
                        entity.setType(type);
                        entity.setReference(String.valueOf(reference));
                        entity.setMessage(type.replace('_', ' '));
                    });
        db.create(
                OutboxEvent::new,
                entity -> {
                    entity.setOrganizationId(org);
                    entity.setEventType(type);
                    entity.setAggregateId(String.valueOf(reference));
                    entity.setPayload(json.writeValueAsString(payload));
                });
    }
}
