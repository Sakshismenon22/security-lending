package com.example.borrower_service;

import static com.example.borrower_service.repository.BorrowerRepository.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.borrower_service.dto.*;
import com.example.borrower_service.dto.LocateRequest;
import com.example.borrower_service.model.*;
import com.example.borrower_service.repository.BorrowerRepository;
import com.example.borrower_service.service.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BorrowerWorkflowTests {
    @Autowired DirectoryService directory;
    @Autowired MarketService market;
    @Autowired TradingService trading;
    @Autowired LendingService lending;
    @Autowired CollateralService collateral;
    @Autowired PaymentService payments;
    @Autowired BorrowerEventProjectionService projections;
    @Autowired BorrowerReadService read;
    @Autowired PortfolioService portfolio;
    @Autowired BorrowerRepository db;
    @Autowired MockMvc mvc;
    @Autowired ApplicationContext context;
    static final AtomicLong sequence = new AtomicLong(1000);

    static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    long org() {
        return n(
                directory.organization(
                        new OrganizationRequest(
                                "Borrower " + UUID.randomUUID(),
                                UUID.randomUUID().toString(),
                                "INR")),
                "id");
    }

    BorrowerEvent event(long org, String type, long ref, long version, Map<String, Object> data) {
        return new BorrowerEvent(
                UUID.randomUUID().toString(), type, org, ref, version, Instant.now(), data);
    }

    void apply(long org, String type, long ref, long version, Map<String, Object> data) {
        projections.apply(event(org, type, ref, version, data));
    }

    void fund(long org, String amount) {
        long id =
                n(
                        payments.funding(
                                org,
                                new FundingRequest(bd(amount), UUID.randomUUID().toString()),
                                true),
                        "id");
        apply(org, "PAYMENT_CONFIRMED", id, 1, Map.of("amount", amount));
    }

    long security(String price) {
        long id = sequence.incrementAndGet();
        market.register(
                new SecurityRequest(id, "DEMO" + id, "NSE", "Demo equity", "INEDEMO" + id, "INR"));
        market.quote(id, new QuoteRequest(bd(price), Instant.now().minusSeconds(120)));
        return id;
    }

    long agreement(long org, long collateralSecurity) {
        long lender = sequence.incrementAndGet();
        apply(
                org,
                "LENDER_REFERENCE_UPDATED",
                lender,
                1,
                Map.of("name", "Demo lender", "externalId", "LENDER_" + lender));
        var rules = new ArrayList<RuleRequest>();
        rules.add(new RuleRequest("CASH", null, BigDecimal.ZERO));
        if (collateralSecurity > 0)
            rules.add(new RuleRequest("SECURITY", collateralSecurity, bd("0.10")));
        long id =
                n(
                        lending.propose(
                                org,
                                new AgreementRequest(
                                        lender,
                                        UUID.randomUUID().toString(),
                                        LocalDate.now().minusDays(1),
                                        LocalDate.now().plusYears(1),
                                        bd("1000000"),
                                        bd("0.03"),
                                        bd("0.01"),
                                        bd("1.05"),
                                        "MONTHLY",
                                        365,
                                        true,
                                        24,
                                        48,
                                        rules)),
                        "id");
        apply(org, "AGREEMENT_ACCEPTED", id, 1, Map.of());
        return id;
    }

    long reservation(long org, long security, long agreement, long quantity) {
        long locate =
                n(
                        lending.locate(
                                org,
                                new LocateRequest(
                                        agreement,
                                        security,
                                        quantity,
                                        Instant.now().plusSeconds(3600))),
                        "id");
        apply(org, "LOCATE_APPROVED", locate, 1, Map.of("approvedQuantity", quantity));
        long id = n(lending.reserve(org, locate, new ReservationRequest(quantity)), "id");
        apply(org, "RESERVATION_CONFIRMED", id, 1, Map.of("quantity", quantity));
        return id;
    }

    long loan(long org, long security, long agreement, long quantity) {
        long reservation = reservation(org, security, agreement, quantity);
        return n(
                lending.createLoan(
                        org,
                        new LoanRequest(
                                reservation,
                                quantity,
                                List.of(new AllocationRequest("CASH", null, 0, bd("2000"))))),
                "id");
    }

    Map<String, Object> account(long org) {
        return db.matchingRow(TraderAccount.class, eq("organizationId", org));
    }

    @Test
    void organizationHasMultipleUsersAndProtectsLastAdmin() {
        long org = org();
        long admin =
                n(
                        directory.member(
                                org, new MemberRequest("Admin", "admin@example.com", "ADMIN")),
                        "id");
        directory.member(org, new MemberRequest("Trader", "trader@example.com", "TRADER"));
        assertEquals(2, db.rows(User.class, eq("organizationId", org)).size());
        assertThrows(
                ResponseStatusException.class,
                () ->
                        directory.updateMember(
                                org, admin, new MemberUpdateRequest("READ_ONLY", false)));
    }

    @Test
    void fundingWaitsForExternalConfirmationAndRetriesDoNotDoubleCredit() {
        long org = org();
        var request = payments.funding(org, new FundingRequest(bd("5000"), "fund-1"), true);
        assertEquals(0, money(account(org), "available").signum());
        var event = event(org, "PAYMENT_CONFIRMED", n(request, "id"), 1, Map.of("amount", "5000"));
        projections.apply(event);
        projections.apply(event);
        assertEquals(0, bd("5000").compareTo(money(account(org), "available")));
        assertEquals(
                n(request, "id"),
                n(payments.funding(org, new FundingRequest(bd("5000"), "fund-1"), true), "id"));
    }

    @Test
    void depositApprovalRequiresAnActiveAdminAndCreditsFunds() {
        long org = org();
        long admin =
                n(
                        directory.member(
                                org, new MemberRequest("Admin", "admin@example.com", "ADMIN")),
                        "id");
        long trader =
                n(
                        directory.member(
                                org, new MemberRequest("Trader", "trader@example.com", "TRADER")),
                        "id");
        long request =
                n(
                        payments.funding(org, new FundingRequest(bd("7500"), "fund-approve"), true),
                        "id");
        assertThrows(
                ResponseStatusException.class, () -> payments.approveDeposit(org, request, trader));
        var approved = payments.approveDeposit(org, request, admin);
        assertEquals(0, bd("7500").compareTo(money(account(org), "available")));
        assertEquals("CONFIRMED", approved.get("status"));
    }

    @Test
    void marketBuyAndOwnedSellUpdateHoldingsCashAndRealizedPnl() {
        long org = org(), security = security("100");
        fund(org, "10000");
        var buy =
                trading.order(org, new OrderRequest(security, "BUY", "MARKET", "OWNED", 10, null));
        assertEquals("FILLED", buy.get("status"));
        market.quote(security, new QuoteRequest(bd("120"), Instant.now().minusSeconds(60)));
        trading.order(org, new OrderRequest(security, "SELL", "MARKET", "OWNED", 5, null));
        var h =
                db.matchingRow(
                        Holding.class, eq("organizationId", org), eq("securityId", security));
        assertEquals(5, n(h, "owned_quantity"));
        assertEquals(0, bd("100").compareTo(money(h, "realized_pnl")));
        assertEquals(0, bd("9600").compareTo(money(account(org), "available")));
    }

    @Test
    void limitOrdersBlockFundsAndCancellationReleasesThem() {
        long org = org(), security = security("100");
        fund(org, "10000");
        long id =
                n(
                        trading.order(
                                org,
                                new OrderRequest(security, "BUY", "LIMIT", "OWNED", 10, bd("90"))),
                        "id");
        assertEquals(0, bd("900").compareTo(money(account(org), "blocked")));
        trading.cancel(org, id);
        assertEquals(0, money(account(org), "blocked").signum());
        assertEquals(0, bd("10000").compareTo(money(account(org), "available")));
    }

    @Test
    void limitFillUsesImprovedPriceAndIsIdempotent() {
        long org = org(), security = security("100");
        fund(org, "10000");
        long id =
                n(
                        trading.order(
                                org,
                                new OrderRequest(security, "BUY", "LIMIT", "OWNED", 10, bd("90"))),
                        "id");
        market.quote(security, new QuoteRequest(bd("80"), Instant.now().minusSeconds(60)));
        trading.match(org, id);
        trading.match(org, id);
        assertEquals(1, db.rows(TradeHistory.class, eq("orderId", id)).size());
        assertEquals(0, bd("9200").compareTo(money(account(org), "available")));
        assertEquals(0, money(account(org), "blocked").signum());
    }

    @Test
    void cachedPriceCanFillBuyButInsufficientInventoryPreventsSell() {
        long org = org(), security = security("100");
        fund(org, "10000");
        assertThrows(
                ResponseStatusException.class,
                () ->
                        trading.order(
                                org,
                                new OrderRequest(security, "SELL", "MARKET", "BORROWED", 1, null)));
        db.edit(
                SecurityMaster.class,
                security,
                entity -> {
                    entity.setPriceAt(OffsetDateTime.now().minusHours(2).toInstant());
                });
        assertEquals(
                "FILLED",
                trading.order(org, new OrderRequest(security, "BUY", "MARKET", "OWNED", 1, null))
                        .get("status"));
        assertEquals(1, db.rows(TradeOrder.class, eq("organizationId", org)).size());
    }

    @Test
    void locateApprovalDoesNotAutomaticallyReserveAndLoanDoesNotAutomaticallySettle() {
        long org = org(), security = security("100");
        fund(org, "10000");
        long agreement = agreement(org, 0);
        long locate =
                n(
                        lending.locate(
                                org,
                                new LocateRequest(
                                        agreement, security, 10, Instant.now().plusSeconds(3600))),
                        "id");
        apply(org, "LOCATE_APPROVED", locate, 1, Map.of("approvedQuantity", 10));
        assertTrue(db.rows(Reservation.class, eq("locateId", locate)).isEmpty());
        long reservation = n(lending.reserve(org, locate, new ReservationRequest(10)), "id");
        assertEquals("REQUESTED", db.owned(Reservation.class, org, reservation).get("status"));
        apply(org, "RESERVATION_CONFIRMED", reservation, 1, Map.of("quantity", 10));
        long loan =
                n(
                        lending.createLoan(
                                org,
                                new LoanRequest(
                                        reservation,
                                        10,
                                        List.of(
                                                new AllocationRequest(
                                                        "CASH", null, 0, bd("1100"))))),
                        "id");
        assertEquals("PENDING_SETTLEMENT", db.owned(Loan.class, org, loan).get("status"));
        assertEquals(
                "PLEDGE_PENDING",
                db.rows(CollateralPosition.class, eq("loanId", loan)).get(0).get("status"));
        assertThrows(
                ResponseStatusException.class,
                () ->
                        trading.order(
                                org,
                                new OrderRequest(security, "SELL", "MARKET", "BORROWED", 1, null)));
    }

    @Test
    void fullBorrowSellCoverReturnFlowWaitsForConfirmations() {
        long org = org(), security = security("100");
        fund(org, "10000");
        long loan = loan(org, security, agreement(org, 0), 10);
        long position = n(db.rows(CollateralPosition.class, eq("loanId", loan)).get(0), "id");
        apply(org, "COLLATERAL_PLEDGED", position, 1, Map.of());
        apply(org, "LOAN_SETTLED", loan, 1, Map.of());
        trading.order(org, new OrderRequest(security, "SELL", "MARKET", "BORROWED", 10, null));
        market.quote(security, new QuoteRequest(bd("90"), Instant.now().minusSeconds(60)));
        trading.order(org, new OrderRequest(security, "BUY", "MARKET", "BORROWED", 10, null));
        long ret = n(lending.requestReturn(org, loan, new ReturnRequest(10)), "id");
        assertEquals(10, n(db.owned(Loan.class, org, loan), "outstanding_quantity"));
        apply(org, "RETURN_SETTLED", ret, 1, Map.of());
        assertEquals("CLOSED", db.owned(Loan.class, org, loan).get("status"));
        collateral.requestRelease(org, position);
        assertEquals(0, bd("2000").compareTo(money(account(org), "pledged")));
        apply(org, "COLLATERAL_RELEASED", position, 2, Map.of());
        assertEquals(0, money(account(org), "pledged").signum());
        assertEquals(0, bd("10100").compareTo(money(account(org), "available")));
    }

    @Test
    void securityCollateralLinksSecurityAndCannotBeSoldWhilePledged() {
        long org = org(), security = security("100"), pledge = security("200");
        fund(org, "10000");
        apply(
                org,
                "HOLDING_IMPORTED",
                pledge,
                1,
                Map.of("ownedQuantity", 20, "borrowedQuantity", 0, "averageCost", "180"));
        long reservation = reservation(org, security, agreement(org, pledge), 10);
        long loan =
                n(
                        lending.createLoan(
                                org,
                                new LoanRequest(
                                        reservation,
                                        10,
                                        List.of(
                                                new AllocationRequest(
                                                        "SECURITY", pledge, 10, BigDecimal.ZERO)))),
                        "id");
        var p = db.rows(CollateralPosition.class, eq("loanId", loan)).get(0);
        assertEquals(pledge, n(p, "security_id"));
        assertThrows(
                ResponseStatusException.class,
                () ->
                        trading.order(
                                org,
                                new OrderRequest(pledge, "SELL", "MARKET", "OWNED", 11, null)));
    }

    @Test
    void reservationsAreUnlockedOnlyByExternalEventNotByBorrowerReleaseRequest() {
        long org = org(),
                security = security("100"),
                reservation = reservation(org, security, agreement(org, 0), 10);
        lending.releaseReservation(org, reservation);
        assertEquals(
                "RELEASE_REQUESTED", db.owned(Reservation.class, org, reservation).get("status"));
        assertEquals(10, n(db.owned(Reservation.class, org, reservation), "remaining_quantity"));
        apply(org, "RESERVATION_RELEASED", reservation, 2, Map.of());
        assertEquals(0, n(db.owned(Reservation.class, org, reservation), "remaining_quantity"));
    }

    @Test
    void externalFeeBillsArePaidOnceWithoutCalculatingFees() throws Exception {
        long org = org(), security = security("100");
        fund(org, "10000");
        long loan = loan(org, security, agreement(org, 0), 10), fee = sequence.incrementAndGet();
        apply(org, "ACCRUAL_REPORTED", fee, 1, Map.of(
                "loanId", loan, "type", "FEE", "date", "2026-11-01",
                "baseValue", "1000", "rate", "0.03", "amount", "0.082192"));
        apply(
                org,
                "FEE_BILL_ISSUED",
                fee,
                1,
                Map.of(
                        "loanId",
                        loan,
                        "periodStart",
                        "2026-11-01",
                        "periodEnd",
                        "2026-11-30",
                        "rate",
                        "0.03",
                        "amount",
                        "123.456789",
                        "dueDate",
                        "2026-12-01"));
        long request = n(payments.payFee(org, fee, "fee-payment"), "id");
        payments.payFee(org, fee, "fee-payment");
        assertEquals("DUE", db.owned(LendingFee.class, org, fee).get("status"));
        assertEquals(0, bd("123.456789").compareTo(money(account(org), "blocked")));
        apply(org, "PAYMENT_CONFIRMED", request, 1, Map.of("amount", "123.456789"));
        assertEquals("PAID", db.owned(LendingFee.class, org, fee).get("status"));
        String path = "/api/v1/organizations/" + org + "/loans/" + loan;
        mvc.perform(get(path + "/fee-accruals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].type").value("FEE"));
        mvc.perform(get(path + "/fees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].status").value("PAID"));
    }

    @Test
    void externalMarginUpdatesAreProjectedWithoutCreatingCallsOrDefaults() {
        long org = org(), security = security("100");
        fund(org, "10000");
        long loan = loan(org, security, agreement(org, 0), 10), call = sequence.incrementAndGet();
        var fields = new HashMap<String, Object>();
        fields.put("loanId", loan);
        fields.put("externalId", "margin-" + call);
        fields.put("amount", "300");
        fields.put("fulfilledAmount", "0");
        fields.put("reason", "End-of-day valuation");
        fields.put("status", "OPEN");
        fields.put("dueAt", Instant.now().minusSeconds(3600).toString());
        apply(org, "MARGIN_CALL_UPDATED", call, 1, fields);
        assertEquals("OPEN", db.owned(MarginCall.class, org, call).get("status"));
        assertTrue(db.rows(LoanDefault.class, eq("organizationId", org)).isEmpty());
        fields.put("status", "OVERDUE");
        apply(org, "MARGIN_CALL_UPDATED", call, 2, fields);
        fields.put("status", "OPEN");
        apply(org, "MARGIN_CALL_UPDATED", call, 1, fields);
        assertEquals("OVERDUE", db.owned(MarginCall.class, org, call).get("status"));
    }

    @Test
    void lifecycleCreatedPendingMarginCallIsProjectedAsOpen() {
        long org = org(), security = security("100");
        fund(org, "10000");
        long loan = loan(org, security, agreement(org, 0), 10), call = sequence.incrementAndGet();
        var fields = new HashMap<String, Object>();
        fields.put("loanId", loan);
        fields.put("externalId", "margin-" + call);
        fields.put("amount", "300");
        fields.put("fulfilledAmount", "0");
        fields.put("reason", "Coverage ratio below the MSLA threshold.");
        fields.put("status", "PENDING");
        fields.put("dueAt", Instant.now().plusSeconds(3600).toString());
        apply(org, "MARGIN_CALL_UPDATED", call, 1, fields);
        assertEquals("OPEN", db.owned(MarginCall.class, org, call).get("status"));
    }

    @Test
    void organizationIsolationPreventsCrossOrganizationOperations() {
        long first = org(), second = org(), security = security("100");
        fund(first, "10000");
        long loan = loan(first, security, agreement(first, 0), 10);
        assertThrows(
                ResponseStatusException.class,
                () -> lending.requestReturn(second, loan, new ReturnRequest(1)));
        assertThrows(ResponseStatusException.class, () -> portfolio.loan(second, loan));
    }

    @Test
    void marginCallResponseAllocatesCollateralButDoesNotFulfillTheCallLocally() {
        long org = org(), security = security("100");
        fund(org, "10000");
        long loan = loan(org, security, agreement(org, 0), 10), call = sequence.incrementAndGet();
        apply(org, "LOAN_SETTLED", loan, 1, Map.of());
        var fields = new HashMap<String, Object>();
        fields.put("loanId", loan);
        fields.put("externalId", "call-" + call);
        fields.put("amount", "300");
        fields.put("fulfilledAmount", "0");
        fields.put("reason", "External EOD check");
        fields.put("status", "OPEN");
        fields.put("dueAt", Instant.now().plusSeconds(3600).toString());
        apply(org, "MARGIN_CALL_UPDATED", call, 1, fields);
        long position =
                n(
                        collateral.respondToMarginCall(
                                org, call, new AllocationRequest("CASH", null, 0, bd("300"))),
                        "id");
        assertEquals(call, n(db.owned(CollateralPosition.class, org, position), "margin_call_id"));
        apply(org, "COLLATERAL_PLEDGED", position, 1, Map.of());
        assertEquals("OPEN", db.owned(MarginCall.class, org, call).get("status"));
        fields.put("fulfilledAmount", "300");
        fields.put("status", "FULFILLED");
        apply(org, "MARGIN_CALL_UPDATED", call, 2, fields);
        assertEquals("FULFILLED", db.owned(MarginCall.class, org, call).get("status"));
    }

    @Test
    void recallResponseWaitsForExternalRecallStatusUpdates() {
        long org = org(), security = security("100");
        fund(org, "10000");
        long loan = loan(org, security, agreement(org, 0), 10), recall = sequence.incrementAndGet();
        apply(org, "LOAN_SETTLED", loan, 1, Map.of());
        var fields =
                Map.<String, Object>of(
                        "loanId",
                        loan,
                        "externalId",
                        "recall-" + recall,
                        "quantity",
                        5,
                        "fulfilledQuantity",
                        0,
                        "reason",
                        "Portfolio sale",
                        "status",
                        "OPEN",
                        "dueAt",
                        Instant.now().plusSeconds(3600).toString());
        apply(org, "RECALL_UPDATED", recall, 1, fields);
        long ret = n(lending.respondToRecall(org, recall, new ReturnRequest(5)), "id");
        assertEquals(recall, n(db.owned(LoanReturn.class, org, ret), "recall_id"));
        apply(org, "RETURN_SETTLED", ret, 1, Map.of());
        assertEquals("OPEN", db.owned(Recall.class, org, recall).get("status"));
        assertEquals(5, n(db.owned(Loan.class, org, loan), "outstanding_quantity"));
        var fulfilled = new HashMap<>(fields);
        fulfilled.put("fulfilledQuantity", 5);
        fulfilled.put("status", "FULFILLED");
        apply(org, "RECALL_UPDATED", recall, 2, fulfilled);
        apply(org, "RECALL_UPDATED", recall, 1, fields);
        assertEquals("FULFILLED", db.owned(Recall.class, org, recall).get("status"));
        assertEquals(5, n(db.owned(Recall.class, org, recall), "fulfilled_quantity"));
    }

    @Test
    void externallyCalculatedRebatesCreditExactlyTheReportedAmount() throws Exception {
        long org = org(), security = security("100");
        fund(org, "10000");
        long loan = loan(org, security, agreement(org, 0), 10), rebate = sequence.incrementAndGet();
        apply(
                org,
                "ACCRUAL_REPORTED",
                rebate,
                1,
                Map.of("loanId", loan, "type", "REBATE", "date", "2026-11-08",
                        "baseValue", "2000", "rate", "0.0123456789", "amount", "12.345678"));
        apply(
                org,
                "REBATE_ACCRUED",
                rebate,
                1,
                Map.of(
                        "loanId",
                        loan,
                        "rate",
                        "0.0123456789",
                        "amount",
                        "12.345678",
                        "date",
                        "2026-11-08",
                        "paymentDate",
                        "2026-12-01",
                        "periodStart",
                        "2026-11-08",
                        "periodEnd",
                        "2026-11-30",
                        "settlementFrequency",
                        "MONTHLY"));
        assertEquals(
                0,
                bd("0.0123456789")
                        .compareTo(money(db.owned(Rebate.class, org, rebate), "rebate_rate")));
        var paid = event(org, "REBATE_PAID", rebate, 2, Map.of("amount", "12.345678"));
        projections.apply(paid);
        projections.apply(paid);
        assertEquals(0, bd("8012.345678").compareTo(money(account(org), "available")));
        String path = "/api/v1/organizations/" + org + "/loans/" + loan;
        mvc.perform(get(path + "/rebate-accruals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(get(path + "/rebate-settlements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].settlementFrequency").value("MONTHLY"));
        mvc.perform(get(path + "/rebate-payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void kafkaConfirmationContractDeserializesAndAppliesWithoutAnyFixEngine() {
        long org = org();
        long request =
                n(payments.funding(org, new FundingRequest(bd("123"), "json-funding"), true), "id");
        var json = tools.jackson.databind.json.JsonMapper.builder().build();
        new KafkaInboundService(projections, market)
                .confirmation(
                        json.writeValueAsString(
                                event(
                                        org,
                                        "PAYMENT_CONFIRMED",
                                        request,
                                        1,
                                        Map.of("amount", "123"))));
        assertEquals(0, bd("123").compareTo(money(account(org), "available")));
    }

    @Test
    void invalidRequestsReturnProblemsAndNoLenderManagementRoutesExist() throws Exception {
        long org = org();
        mvc.perform(
                        post("/api/v1/organizations/" + org + "/orders")
                                .contentType("application/json")
                                .content(
                                        "{\"securityId\":1,\"side\":\"BUY\",\"type\":\"MARKET\",\"source\":\"OWNED\",\"quantity\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("detail").value("Request validation failed"));
        for (String path : List.of("defaults", "buy-ins", "recalls", "margin-calls"))
            mvc.perform(post("/api/v1/organizations/" + org + "/" + path))
                    .andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/api/v1/organizations/" + org + "/loans/1/accrue"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/organizations/" + org + "/loans/1/risk-review"))
                .andExpect(status().isNotFound());
        assertFalse(context.containsBean("fixAdapter"));
        assertFalse(context.containsBean("riskService"));
        assertFalse(context.containsBean("accountingService"));
    }

    @Test
    void paginatedReadsAndLoanViewsAreAvailableForFrontend() throws Exception {
        long org = org();
        mvc.perform(get("/api/v1/organizations/" + org + "/orders?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.totalElements").value(0));
        mvc.perform(get("/api/v1/organizations/" + org + "/orders?size=201"))
                .andExpect(status().isBadRequest());
        assertNotNull(portfolio.dashboard(org));
    }
}
