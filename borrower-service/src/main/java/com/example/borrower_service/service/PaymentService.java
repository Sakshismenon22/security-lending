package com.example.borrower_service.service;

import static com.example.borrower_service.repository.BorrowerRepository.*;

import com.example.borrower_service.dto.FundingRequest;
import com.example.borrower_service.model.*;
import com.example.borrower_service.repository.BorrowerRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Borrower initiates payments. Only external confirmations settle them. No fee/rebate calculations
 * live here.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentService {
    private final BorrowerRepository db;
    private final DirectoryService accounts;
    private final EventWriter events;

    public Map<String, Object> funding(long org, FundingRequest r, boolean deposit) {
        return request(
                org, deposit ? "DEPOSIT" : "WITHDRAWAL", r.amount(), r.clientRequestId(), null);
    }

    public Map<String, Object> approveDeposit(long org, long requestId, long approverUserId) {
        db.lock(org);
        var request = db.owned(PaymentRequest.class, org, requestId);
        require("DEPOSIT".equals(request.get("type")), "Only deposit requests can be approved");
        require("PENDING".equals(request.get("status")), "Payment is no longer pending");
        var approver =
                db.matchingRow(
                        User.class,
                        eq("organizationId", org),
                        eq("id", approverUserId),
                        eq("active", true));
        long adminRole = n(db.matchingRow(Role.class, eq("roleName", "ADMIN")), "id");
        require(
                n(approver, "role_id") == adminRole,
                "Only an active administrator may approve a deposit");
        return applyConfirmation(org, requestId, true, money(request, "amount"));
    }

    public Map<String, Object> payFee(long org, long feeId, String key) {
        db.lock(org);
        var fee = db.owned(LendingFee.class, org, feeId);
        // A client retry returns the original pending/confirmed payment rather than charging again.
        var retry =
                db.rows(
                        PaymentRequest.class,
                        eq("organizationId", org),
                        eq("clientRequestId", key));
        if (!retry.isEmpty()) {
            var old = retry.get(0);
            require(
                    old.get("fee_id") != null && n(old, "fee_id") == feeId,
                    "Idempotency key was used for a different payment");
            return old;
        }
        require(
                "DUE".equals(fee.get("status")),
                "Only an externally issued, unpaid bill can be paid");
        require(
                db.rows(PaymentRequest.class, eq("feeId", feeId), eq("status", "PENDING"))
                        .isEmpty(),
                "Fee payment already pending");
        return request(org, "FEE", money(fee, "fee_amount"), key, feeId);
    }

    private Map<String, Object> request(
            long org, String type, BigDecimal value, String key, Long fee) {
        db.lock(org);
        require(
                key != null && !key.isBlank() && key.length() <= 120,
                "A client request ID of at most 120 characters is required");
        var retry =
                db.rows(
                        PaymentRequest.class,
                        eq("organizationId", org),
                        eq("clientRequestId", key));
        if (!retry.isEmpty()) {
            var old = retry.get(0);
            require(
                    type.equals(old.get("type")) && value.compareTo(money(old, "amount")) == 0,
                    "Idempotency key was used with different payment details");
            return old;
        }
        require(value.signum() > 0, "Payment amount must be positive");
        long id =
                db.create(
                        PaymentRequest::new,
                        entity -> {
                            entity.setOrganizationId(org);
                            entity.setFeeId(fee);
                            entity.setType(type);
                            entity.setAmount(value);
                            entity.setClientRequestId(key);
                            entity.setStatus("PENDING");
                        });
        if (!"DEPOSIT".equals(type)) {
            accounts.changeCash(org, value.negate(), "PAYMENT_CASH_BLOCK", String.valueOf(id));
            db.editMatching(
                    TraderAccount.class,
                    entity -> {
                        entity.setBlocked(entity.getBlocked().add(value));
                    },
                    eq("organizationId", org));
        }
        events.write(
                org,
                type + "_REQUESTED",
                id,
                Map.of("amount", value, "clientRequestId", key, "feeId", fee == null ? 0 : fee));
        return db.owned(PaymentRequest.class, org, id);
    }

    Map<String, Object> applyConfirmation(
            long org, long id, boolean confirmed, BigDecimal actualAmount) {
        var request = db.owned(PaymentRequest.class, org, id);
        require("PENDING".equals(request.get("status")), "Payment is no longer pending");
        BigDecimal value = money(request, "amount");
        require(
                actualAmount != null && actualAmount.compareTo(value) == 0,
                "Confirmation amount does not match payment request");
        if ("DEPOSIT".equals(request.get("type"))) {
            if (confirmed) accounts.changeCash(org, value, "DEPOSIT_CONFIRMED", String.valueOf(id));
        } else {
            db.editMatching(
                    TraderAccount.class,
                    entity -> {
                        entity.setBlocked(entity.getBlocked().subtract(value));
                    },
                    eq("organizationId", org));
            if (!confirmed) accounts.changeCash(org, value, "PAYMENT_REFUNDED", String.valueOf(id));
            if (confirmed && "FEE".equals(request.get("type")))
                db.edit(
                        LendingFee.class,
                        n(request, "fee_id"),
                        entity -> {
                            entity.setStatus("PAID");
                            entity.setPaidAt(Instant.now());
                        });
        }
        db.edit(
                PaymentRequest.class,
                id,
                entity -> {
                    entity.setStatus(confirmed ? "CONFIRMED" : "REJECTED");
                    entity.setConfirmedAt(Instant.now());
                });
        return db.owned(PaymentRequest.class, org, id);
    }
}
