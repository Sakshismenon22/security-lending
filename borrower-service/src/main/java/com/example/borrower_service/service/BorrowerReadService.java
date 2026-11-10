package com.example.borrower_service.service;

import com.example.borrower_service.model.Organization;
import com.example.borrower_service.model.Loan;
import com.example.borrower_service.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true)
public class BorrowerReadService {
    public Object loanBilling(long org, long loanId, String resource, int page, int size) {
        db.owned(Loan.class, org, loanId);
        if (page < 0 || size < 1 || size > 200)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be nonnegative and size between 1 and 200");
        var paging = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        return switch (resource) {
            case "rebate-accruals" -> feeAccrualViewRepository.findByOrganizationIdAndLoanIdAndType(org, loanId, "REBATE", paging);
            case "rebate-settlements" -> rebateRepository.findByOrganizationIdAndLoanId(org, loanId, paging);
            case "rebate-payments" -> rebatePaymentRepository.findByOrganizationIdAndLoanId(org, loanId, paging);
            case "fee-accruals" -> feeAccrualViewRepository.findByOrganizationIdAndLoanIdAndType(
                    org, loanId, "FEE", paging.withSort(Sort.by(Sort.Direction.DESC, "accrualDate", "id")));
            case "fees" -> lendingFeeRepository.findByOrganizationIdAndLoanId(
                    org, loanId, paging.withSort(Sort.by(Sort.Direction.DESC, "periodEnd", "id")));
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown billing resource");
        };
    }
    private final BorrowerRepository db;
    private final UserRepository usersRepository;
    private final TraderAccountRepository traderAccountRepository;
    private final HoldingRepository holdingsRepository;
    private final MslaRepository mslaRepository;
    private final LocateRequestRepository locateRequestRepository;
    private final ReservationRepository reservationRepository;
    private final LoanRepository loanRepository;
    private final CollateralPositionRepository collateralPositionRepository;
    private final CollateralHistoryRepository collateralHistoryRepository;
    private final LoanReturnRepository loanReturnRepository;
    private final TradeOrderRepository tradeOrderRepository;
    private final TradeHistoryRepository tradeHistoryRepository;
    private final TraderAccountTransactionRepository traderAccountTransactionRepository;
    private final LoanDefaultRepository loanDefaultRepository;
    private final BuyInRepository buyInRepository;
    private final AuditEventRepository auditEventRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ShortLotRepository shortLotRepository;
    private final MarginCallRepository marginRecallRepository;
    private final RecallRepository recallRepository;
    private final LendingFeeRepository lendingFeeRepository;
    private final RebateRepository rebateRepository;
    private final RebatePaymentRepository rebatePaymentRepository;
    private final CreditHistoryRepository creditHistoryRepository;
    private final WatchlistEntryRepository watchlistEntryRepository;
    private final NotificationRepository notificationRepository;
    private final LoanValuationRepository loanValuationRepository;
    private final PaymentRequestRepository paymentRequestRepository;
    private final FeeAccrualViewRepository feeAccrualViewRepository;
    public Object list(long org,String resource) {return list(org,resource,0,50);}
    public Object list(long org,String resource,int page,int size) {
        db.find(Organization.class, org);
        if(page<0 || size<1 || size>200)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"page must be nonnegative and size between 1 and 200");
        var pageable=PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"id"));
        return switch(resource) {
            case "users" -> usersRepository.findByOrganizationId(org,pageable);
            case "trader_account" -> traderAccountRepository.findByOrganizationId(org,PageRequest.of(page,size));
            case "holdings" -> holdingsRepository.findByOrganizationId(org,PageRequest.of(page,size));
            case "msla" -> mslaRepository.findByOrganizationId(org,pageable);
            case "locate_request" -> locateRequestRepository.findByOrganizationId(org,pageable);
            case "reservation" -> reservationRepository.findByOrganizationId(org,pageable);
            case "loan" -> loanRepository.findByOrganizationId(org,pageable);
            case "collateral_position" -> collateralPositionRepository.findByOrganizationId(org,pageable);
            case "collateral_history" -> collateralHistoryRepository.findByOrganizationId(org,pageable);
            case "loan_return" -> loanReturnRepository.findByOrganizationId(org,pageable);
            case "trade_order" -> tradeOrderRepository.findByOrganizationId(org,pageable);
            case "trade_history" -> tradeHistoryRepository.findByOrganizationId(org,pageable);
            case "trader_account_transaction" -> traderAccountTransactionRepository.findByOrganizationId(org,pageable);
            case "loan_default" -> loanDefaultRepository.findByOrganizationId(org,pageable);
            case "buy_in" -> buyInRepository.findByOrganizationId(org,pageable);
            case "audit_event" -> auditEventRepository.findByOrganizationId(org,pageable);
            case "outbox_event" -> outboxEventRepository.findByOrganizationId(org,pageable);
            case "short_lot" -> shortLotRepository.findByOrganizationId(org,pageable);
            case "margin_recall" -> marginRecallRepository.findByOrganizationId(org,pageable);
            case "recall" -> recallRepository.findByOrganizationId(org,pageable);
            case "lending_fee" -> lendingFeeRepository.findByOrganizationId(org,pageable);
            case "rebate" -> rebateRepository.findByOrganizationId(org,pageable);
            case "rebate_payment" -> rebatePaymentRepository.findByOrganizationId(org,pageable);
            case "credit_history" -> creditHistoryRepository.findByOrganizationId(org,pageable);
            case "watchlist_entry" -> watchlistEntryRepository.findByOrganizationId(org,pageable);
            case "notification" -> notificationRepository.findByOrganizationId(org,pageable);
            case "loan_valuation" -> loanValuationRepository.findByOrganizationId(org,pageable);
            case "payment_request" -> paymentRequestRepository.findByOrganizationId(org,pageable);
            case "fee_accrual_view" -> feeAccrualViewRepository.findByOrganizationId(org,pageable);
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Unknown resource");
        };
    }
}
