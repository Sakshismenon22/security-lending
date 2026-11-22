package com.example.lender_service.service;

import static com.example.lender_service.service.Checks.*;

import com.example.lender_service.model.LenderAccount;
import com.example.lender_service.model.LenderAccountLedger;
import com.example.lender_service.model.LenderDepositRequest;
import com.example.lender_service.repository.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class LenderFundingService {
  private final TenantService tenants;
  private final LenderAccountRepository accounts;
  private final LenderDepositRequestRepository deposits;
  private final LenderAccountLedgerRepository ledger;
  private final BuyinOrderRepository orders;
  private final Clock clock;

  @Transactional(readOnly = true)
  public Map<String, BigDecimal> balances(long org) {
    BigDecimal balance = account(org).getFundingBalance();
    BigDecimal reserved = orders.reservedFunding(org, null);
    return Map.of("balance", balance, "reserved", reserved, "available", balance.subtract(reserved));
  }

  @Transactional(readOnly = true)
  public Page<LenderDepositRequest> list(long org, int page) {
    return deposits.findByOrganizationId(org, PageRequest.of(page, 20, Sort.by("id").descending()));
  }

  public LenderDepositRequest request(long org, String key, BigDecimal amount) {
    tenants.lock(org);
    var previous = deposits.findByOrganizationIdAndClientRequestId(org, key);
    if (previous.isPresent()) {
      var existing = previous.get();
      require(existing.getAmount().compareTo(amount) == 0,
          "Deposit reference was used with a different amount");
      return existing;
    }
    var deposit = new LenderDepositRequest();
    deposit.setOrganizationId(org);
    deposit.setClientRequestId(key);
    deposit.setAmount(amount);
    deposit.setStatus("PENDING");
    deposit.setCreatedAt(clock.instant());
    return deposits.save(deposit);
  }

  public LenderDepositRequest approve(long org, long id, long admin) {
    tenants.lock(org);
    var deposit = found(deposits.findByOrganizationIdAndId(org, id));
    if ("CONFIRMED".equals(deposit.getStatus())) return deposit;
    var account = account(org);
    BigDecimal balance = account.getFundingBalance().add(deposit.getAmount());
    require(balance.precision() - balance.scale() <= 18, "Funding balance would exceed the account limit");
    account.setFundingBalance(balance);
    deposit.setStatus("CONFIRMED");
    deposit.setApprovedAt(clock.instant());
    deposit.setApprovedBy(admin);
    post(org, null, "LENDER_FUNDING_DEPOSIT", deposit.getAmount(), "DEPOSIT-" + id);
    return deposit;
  }

  BigDecimal availableFor(long org, Long loan) {
    return account(org).getFundingBalance().subtract(orders.reservedFunding(org, loan));
  }

  void applyToBuyin(long org, long loan, BigDecimal amount, String reference) {
    if (amount.signum() == 0) return;
    tenants.lock(org);
    require(availableFor(org, loan).compareTo(amount) >= 0,
        "Insufficient lender funds for the buy-in shortfall");
    var account = account(org);
    account.setFundingBalance(account.getFundingBalance().subtract(amount));
    post(org, loan, "LENDER_FUNDING_BUY_IN", amount.negate(), reference);
  }

  private LenderAccount account(long org) {
    return found(accounts.findByOrganizationId(org));
  }

  private void post(long org, Long loan, String type, BigDecimal amount, String reference) {
    var entry = new LenderAccountLedger();
    entry.setOrganizationId(org);
    entry.setLoanId(loan);
    entry.setType(type);
    entry.setAmount(amount);
    entry.setReference(reference);
    entry.setCreatedAt(clock.instant());
    ledger.save(entry);
  }
}
