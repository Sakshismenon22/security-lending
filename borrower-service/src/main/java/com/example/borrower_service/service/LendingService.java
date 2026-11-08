package com.example.borrower_service.service;

import com.example.borrower_service.repository.BorrowerRepository;
import com.example.borrower_service.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.*;
import java.util.*;
import static com.example.borrower_service.repository.BorrowerRepository.*;

@Service
@Transactional
public class LendingService {
    private final BorrowerRepository db; private final EventWriter events; private final MarketService market;
    private final CollateralService collateral; private final Clock clock;
    public LendingService(BorrowerRepository db,EventWriter events,MarketService market,CollateralService collateral,Clock clock) {
        this.db=db;this.events=events;this.market=market;this.collateral=collateral;this.clock=clock;
    }
    public Map<String,Object> propose(long org,AgreementRequest r) {
        db.lock(org);db.one("select id from lender_reference where id=?",r.lenderId());
        require(!r.expiryDate().isBefore(r.effectiveDate()),"Agreement expires before its effective date");
        require(r.dayBasis()==360 || r.dayBasis()==365,"Day basis must be 360 or 365");
        var seen=new HashSet<String>();
        for(var rule:r.collateralRules()) {
            boolean cash="CASH".equals(rule.assetType());
            require(cash?(rule.securityId()==null && rule.haircut().signum()==0):rule.securityId()!=null,"Cash has no security or haircut; security collateral requires securityId");
            if(!cash)db.one("select id from security_master where id=?",rule.securityId());
            require(seen.add(rule.assetType()+":"+rule.securityId()),"Duplicate collateral eligibility rule");
        }
        long id=db.insert("insert into msla(organization_id,lender_id,number,status,effective_date,expiry_date,credit_limit,fee_rate,rebate_rate,coverage_ratio,billing_frequency,day_basis,recall_allowed,margin_hours,recall_hours) values (?,?,?,'PROPOSED',?,?,?,?,?,?,?,?,?,?,?)",org,r.lenderId(),r.number(),r.effectiveDate(),r.expiryDate(),r.creditLimit(),r.feeRate(),r.rebateRate(),r.coverageRatio(),r.billingFrequency(),r.dayBasis(),r.recallAllowed(),r.marginHours(),r.recallHours());
        for(var rule:r.collateralRules()) {
            long asset=n(db.one("select id from assets where asset_name=?",rule.assetType()),"id");
            db.insert("insert into collateral_haircut(msla_id,asset_type,security_id,haircut,asset_id) values (?,?,?,?,?)",id,rule.assetType(),rule.securityId(),rule.haircut(),asset);
        }
        events.write(org,"AGREEMENT_PROPOSED",id,r);return db.owned("msla",org,id);
    }
    private Map<String,Object> active(long org,long id) {
        var a=db.owned("msla",org,id);var today=LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata")));
        require("ACTIVE".equals(a.get("status")),"MSLA is not active");
        require(!today.isBefore(date(a,"effective_date")) && !today.isAfter(date(a,"expiry_date")),"MSLA is outside its effective dates");return a;
    }
    public Map<String,Object> locate(long org,LocateRequest r) {
        db.lock(org); active(org,r.agreementId());db.one("select id from security_master where id=?",r.securityId());
        require(r.expiresAt().isAfter(Instant.now()),"Locate expiry must be in the future");
        long id=db.insert("insert into locate_request(organization_id,msla_id,security_id,quantity,status,expires_at) values (?,?,?,?,'PENDING',?)",org,r.agreementId(),r.securityId(),r.quantity(),r.expiresAt());
        events.write(org,"LOCATE_REQUESTED",id,r);return db.owned("locate_request",org,id);
    }
    public Map<String,Object> releaseReservation(long org,long id) {
        db.lock(org); var r=db.owned("reservation",org,id);require("ACTIVE".equals(r.get("status")),"Reservation is not active");
        db.update("update reservation set status='RELEASE_REQUESTED' where id=?",id);
        events.write(org,"RESERVATION_RELEASE_REQUESTED",id,Map.of("quantity",n(r,"remaining_quantity"))); return db.owned("reservation",org,id);
    }
    public Map<String,Object> reserve(long org,long locateId,ReservationRequest request) {
        db.lock(org);var locate=db.owned("locate_request",org,locateId);
        require(Set.of("APPROVED","PARTIALLY_APPROVED").contains(locate.get("status")),"Locate must be approved before reservation");
        require(instant(locate,"expires_at").isAfter(Instant.now()),"Locate approval has expired");
        require(request.quantity()<=n(locate,"approved_quantity"),"Reservation exceeds approved locate quantity");
        require(db.list("select id from reservation where locate_id=?",locateId).isEmpty(),"This locate already has a reservation request");
        long id=db.insert("insert into reservation(organization_id,locate_id,msla_id,security_id,quantity,remaining_quantity,status,expires_at) values (?,?,?,?,?,0,'REQUESTED',?)",org,locateId,n(locate,"msla_id"),n(locate,"security_id"),request.quantity(),instant(locate,"expires_at"));
        events.write(org,"RESERVATION_REQUESTED",id,Map.of("locateId",locateId,"quantity",request.quantity()));
        return db.owned("reservation",org,id);
    }
    public BigDecimal exposure(long org,long agreement) {
        BigDecimal result=BigDecimal.ZERO;
        for(var l:db.list("select * from loan where organization_id=? and msla_id=? and status in ('PENDING_SETTLEMENT','ACTIVE','RETURN_PENDING','DEFAULTED')",org,agreement))
            result=result.add(market.price(n(l,"security_id")).value().multiply(BigDecimal.valueOf(n(l,"outstanding_quantity"))));
        return amount(result);
    }
    public Map<String,Object> createLoan(long org,LoanRequest r) {
        db.lock(org);var reservation=db.owned("reservation",org,r.reservationId());
        require("ACTIVE".equals(reservation.get("status")) && instant(reservation,"expires_at").isAfter(Instant.now()),"Reservation is not active or has expired");
        require(n(reservation,"remaining_quantity")>=r.quantity(),"Loan quantity exceeds remaining reservation");
        var a=active(org,n(reservation,"msla_id"));
        BigDecimal before=exposure(org,n(a,"id")),value=market.price(n(reservation,"security_id")).value().multiply(BigDecimal.valueOf(r.quantity()));
        require(before.add(value).compareTo(money(a,"credit_limit"))<=0,"MSLA credit limit exceeded");
        long id=db.insert("insert into loan(organization_id,msla_id,reservation_id,security_id,quantity,outstanding_quantity,status,fee_rate,rebate_rate,coverage_ratio,day_basis,billing_frequency) values (?,?,?,?,?,?,'PENDING_SETTLEMENT',?,?,?,?,?)",org,n(a,"id"),r.reservationId(),n(reservation,"security_id"),r.quantity(),r.quantity(),money(a,"fee_rate"),money(a,"rebate_rate"),money(a,"coverage_ratio"),n(a,"day_basis"),a.get("billing_frequency"));
        db.update("update reservation set remaining_quantity=remaining_quantity-? where id=?",r.quantity(),r.reservationId());
        if(n(reservation,"remaining_quantity")==r.quantity())db.update("update reservation set status='CONSUMED' where id=?",r.reservationId());
        for(var allocation:r.collateral())collateral.post(org,id,allocation);
        collateral.validateInitialAllocation(org,id);credit(org,a,"LOAN_REQUESTED",before,before.add(value));
        events.write(org,"LOAN_REQUESTED",id,r);return db.owned("loan",org,id);
    }
    public Map<String,Object> requestReturn(long org,long loanId,ReturnRequest r) {
        db.lock(org);var loan=db.owned("loan",org,loanId);
        require(Set.of("ACTIVE","RETURN_PENDING").contains(loan.get("status")),"Loan is not active");
        if(r.recallId()!=null) {
            var recall=db.owned("recall",org,r.recallId());
            require(n(recall,"loan_id")==loanId && Set.of("OPEN","PARTIALLY_FULFILLED","OVERDUE").contains(recall.get("status")),"Recall does not belong to this loan or is already resolved");
            long pendingForRecall=((Number)db.one("select coalesce(sum(quantity),0) as quantity from loan_return where recall_id=? and status='PENDING'",r.recallId()).get("quantity")).longValue();
            require(r.quantity()<=n(recall,"quantity")-n(recall,"fulfilled_quantity")-pendingForRecall,"Return exceeds the uncommitted recall quantity");
        }
        long pending=((Number)db.one("select coalesce(sum(quantity),0) as quantity from loan_return where loan_id=? and status='PENDING'",loanId).get("quantity")).longValue();
        require(r.quantity()<=n(loan,"outstanding_quantity")-pending,"Return exceeds uncommitted outstanding loan quantity");
        long security=n(loan,"security_id");db.holding(org,security);var h=db.one("select * from holdings where organization_id=? and security_id=?",org,security);
        long borrowed=Math.min(r.quantity(),n(h,"borrowed_quantity")-n(h,"blocked_borrowed"));
        long owned=r.quantity()-borrowed;
        require(n(h,"owned_quantity")-n(h,"pledged_quantity")-n(h,"blocked_owned")>=owned,"Buy back sufficient unpledged securities before returning the loan");
        db.update("update holdings set owned_quantity=owned_quantity-?,borrowed_quantity=borrowed_quantity-? where organization_id=? and security_id=?",owned,borrowed,org,security);
        long id=db.insert("insert into loan_return(organization_id,loan_id,quantity,owned_quantity,borrowed_quantity,status,owned_cost,recall_id) values (?,?,?,?,?,'PENDING',?,?)",org,loanId,r.quantity(),owned,borrowed,money(h,"average_cost"),r.recallId());
        db.update("update loan set status='RETURN_PENDING' where id=?",loanId);
        events.write(org,"RETURN_REQUESTED",id,Map.of("loanId",loanId,"quantity",r.quantity(),"recallId",r.recallId()==null?0:r.recallId()));return db.owned("loan_return",org,id);
    }
    public Map<String,Object> respondToRecall(long org,long recallId,ReturnRequest r) {
        db.lock(org);var recall=db.owned("recall",org,recallId);
        require(r.recallId()==null || r.recallId()==recallId,"Recall reference does not match the request path");
        return requestReturn(org,n(recall,"loan_id"),new ReturnRequest(r.quantity(),recallId));
    }
    private void credit(long org,Map<String,Object> a,String type,BigDecimal before,BigDecimal after) {
        BigDecimal limit=money(a,"credit_limit");
        db.insert("insert into credit_history(organization_id,msla_id,transaction_type,old_limit,new_limit,credit_change,utilized_before,utilized_after,available_before,available_after) values (?,?,?,?,?,?,?,?,?,?)",org,n(a,"id"),type,limit,limit,after.subtract(before),before,after,limit.subtract(before),limit.subtract(after));
    }
}
