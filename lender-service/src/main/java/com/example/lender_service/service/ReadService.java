package com.example.lender_service.service;

import static com.example.lender_service.service.Checks.*;

import com.example.lender_service.model.*;
import com.example.lender_service.repository.*;
import com.example.lender_service.response.PageResponse;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReadService {
  private final TenantService tenants;
  private final LenderAccountRepository accounts;
  private final SecurityMasterRepository securities;
  private final CollateralHaircutRepository haircuts;
  private final CollateralService preview;
  private final JsonService json;
  private final NotificationRepository notification;
  private final BorrowerConnectionRepository borrowerConnection;
  private final LendingFeeRepository lendingFee;
  private final CollateralPositionRepository collateralPosition;
  private final RecallRepository recall;
  private final MarginCallRepository marginCall;
  private final InventoryHistoryRepository inventoryHistory;
  private final InventoryPositionRepository inventoryPosition;
  private final BuyInRepository buyIn;
  private final LoanRepository loan;
  private final AuditEventRepository auditEvent;
  private final MslaRepository msla;
  private final LenderAccountLedgerRepository lenderAccountLedger;
  private final RebateRepository rebate;
  private final LifecycleRequestRepository lifecycleRequest;
  private final RebatePaymentRepository rebatePayment;
  private final ReservationRepository reservation;
  private final DailyAccrualRepository dailyAccrual;
  private final UserRepository user;
  private final LoanValuationRepository loanValuation;
  private final LocateRequestRepository locateRequest;
  private final LoanReturnRepository loanReturn;
  private final CollateralHistoryRepository collateralHistory;
  private final LoanDefaultRepository loanDefault;

  public Object list(long org, String resource, int page, int size) {
    tenants.exists(org);
    if (page < 0 || size < 1 || size > 200)
      throw new IllegalArgumentException("page must be nonnegative; size must be 1..200");
    var paging = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
    return switch (resource) {
      case "users" -> PageResponse.from(user.findByOrganizationId(org, paging));
      case "borrowers" -> PageResponse.from(borrowerConnection.findByOrganizationId(org, paging));
      case "inventory" -> PageResponse.from(inventoryPosition.findByOrganizationId(org, paging));
      case "inventory-history" ->
          PageResponse.from(inventoryHistory.findByOrganizationId(org, paging));
      case "agreements" -> PageResponse.from(msla.findByOrganizationId(org, paging));
      case "locates" -> PageResponse.from(locateRequest.findByOrganizationId(org, paging));
      case "reservations" -> PageResponse.from(reservation.findByOrganizationId(org, paging));
      case "loans" -> PageResponse.from(loan.findByOrganizationId(org, paging));
      case "collateral" -> PageResponse.from(collateralPosition.findByOrganizationId(org, paging));
      case "collateral-history" ->
          PageResponse.from(collateralHistory.findByOrganizationId(org, paging));
      case "returns" -> PageResponse.from(loanReturn.findByOrganizationId(org, paging));
      case "lifecycle-requests" ->
          PageResponse.from(lifecycleRequest.findByOrganizationId(org, paging));
      case "valuations" -> PageResponse.from(loanValuation.findByOrganizationId(org, paging));
      case "margin-calls" -> PageResponse.from(marginCall.findByOrganizationId(org, paging));
      case "recalls" -> PageResponse.from(recall.findByOrganizationId(org, paging));
      case "fees" -> PageResponse.from(lendingFee.findByOrganizationId(org, paging));
      case "rebates" -> PageResponse.from(rebate.findByOrganizationId(org, paging));
      case "accruals" -> PageResponse.from(dailyAccrual.findByOrganizationId(org, paging));
      case "defaults" -> PageResponse.from(loanDefault.findByOrganizationId(org, paging));
      case "buy-ins" -> PageResponse.from(buyIn.findByOrganizationId(org, paging));
      case "rebate-payments" -> PageResponse.from(rebatePayment.findByOrganizationId(org, paging));
      case "ledger" -> PageResponse.from(lenderAccountLedger.findByOrganizationId(org, paging));
      case "notifications" -> PageResponse.from(notification.findByOrganizationId(org, paging));
      case "audit" -> PageResponse.from(auditEvent.findByOrganizationId(org, paging));
      default -> throw new IllegalArgumentException("Unknown resource");
    };
  }

  public Object agreement(long org, long id) {
    var a = found(msla.findByIdAndOrganizationId(id, org));
    return Map.of(
        "agreement",
        a,
        "terms",
        json.tree(a.getTerms()),
        "collateralRules",
        haircuts.findByMslaId(id));
  }

  public Object loan(long org, long id) {
    var l = found(loan.findByIdAndOrganizationId(id, org));
    var result = new LinkedHashMap<String, Object>();
    result.put("loan", l);
    result.put("agreement", found(msla.findById(l.getMslaId())));
    result.put("borrower", found(borrowerConnection.findById(l.getConnectionId())));
    result.put("security", found(securities.findById(l.getSecurityId())));
    result.put("collateral", collateralPosition.findByLoanId(id));
    result.put("returns", loanReturn.findByLoanId(id));
    result.put(
        "valuation", loanValuation.findFirstByLoanIdOrderByValuedAtDescIdDesc(id).orElse(null));
    try {
      result.put("collateralPreview", preview.preview(org, id, true, null));
    } catch (org.springframework.web.server.ResponseStatusException e) {
      result.put("previewError", e.getReason());
    }
    return result;
  }

  public Object account(long org) {
    tenants.exists(org);
    return found(accounts.findByOrganizationId(org));
  }

  public Object projection(long org, String resource, long id) {
    LifecycleProjection value =
        switch (resource) {
          case "valuations" -> found(loanValuation.findByIdAndOrganizationId(id, org));
          case "margin-calls" -> found(marginCall.findByIdAndOrganizationId(id, org));
          case "recalls" -> found(recall.findByIdAndOrganizationId(id, org));
          case "fees" -> found(lendingFee.findByIdAndOrganizationId(id, org));
          case "rebates" -> found(rebate.findByIdAndOrganizationId(id, org));
          case "accruals" -> found(dailyAccrual.findByIdAndOrganizationId(id, org));
          case "defaults" -> found(loanDefault.findByIdAndOrganizationId(id, org));
          case "buy-ins" -> found(buyIn.findByIdAndOrganizationId(id, org));
          default -> throw new IllegalArgumentException("Unknown projection");
        };
    return Map.of("view", value, "reportedData", json.tree(value.getSourceData()));
  }

  public Object dashboard(long org) {
    tenants.exists(org);
    var stock = inventoryPosition.findByOrganizationId(org, Pageable.unpaged()).getContent();
    var allLoans = loan.findByOrganizationId(org, Pageable.unpaged()).getContent();
    return Map.of(
        "account",
        account(org),
        "totalQuantity",
        stock.stream().mapToLong(InventoryPosition::getTotalQuantity).sum(),
        "availableQuantity",
        stock.stream().mapToLong(InventoryPosition::getAvailableQuantity).sum(),
        "reservedQuantity",
        stock.stream().mapToLong(InventoryPosition::getReservedQuantity).sum(),
        "loanedQuantity",
        stock.stream().mapToLong(InventoryPosition::getLoanedQuantity).sum(),
        "activeLoans",
        allLoans.stream().filter(l -> l.getStatus().equals("ACTIVE")).count(),
        "pendingLoans",
        allLoans.stream().filter(l -> l.getStatus().equals("PENDING_REVIEW")).count(),
        "recentNotifications",
        notification
            .findByOrganizationId(org, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "id")))
            .getContent());
  }

  @Transactional
  public void markRead(long org, long id) {
    tenants.lock(org);
    found(notification.findByIdAndOrganizationId(id, org)).setIsRead(true);
  }
}
