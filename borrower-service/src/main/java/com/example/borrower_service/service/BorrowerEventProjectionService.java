package com.example.borrower_service.service;

import com.example.borrower_service.dto.BorrowerEvent;
import com.example.borrower_service.repository.BorrowerRepository;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.*;
import java.util.*;
import static com.example.borrower_service.repository.BorrowerRepository.*;

/** Applies authoritative confirmations to borrower state. It never approves, calculates, schedules, or executes lender actions. */
@Service
@RequiredArgsConstructor
@Transactional
public class BorrowerEventProjectionService {
    private final BorrowerRepository db;private final CollateralService collateral;private final DirectoryService accounts;
    private final PaymentService payments;private final EventWriter events;private final Validator validator;

    public Map<String,Object> apply(BorrowerEvent event) {
        require(validator.validate(event).isEmpty(),"Invalid confirmation envelope");
        long org=event.organizationId(),id=event.referenceId();db.lock(org);
        if(!db.list("select event_id from inbox_event where event_id=?",event.eventId()).isEmpty())return Map.of("duplicate",true);
        String resource=resource(event.type())+(event.type().equals("LENDER_REFERENCE_UPDATED")?"":":"+org);
        // Some projections share a resource version, e.g. reservation confirmation and expiry.
        var cursor=db.list("select * from projection_cursor where resource=? and reference_id=? for update",resource,id);
        if(!cursor.isEmpty() && n(cursor.get(0),"version")>=event.version()) {
            db.update("insert into inbox_event(event_id) values (?)",event.eventId());return Map.of("stale",true);
        }
        var d=event.data();
        switch(event.type()) {
            case "LENDER_REFERENCE_UPDATED" -> lender(id,d);
            case "AGREEMENT_ACCEPTED","AGREEMENT_REJECTED" -> {
                var a=db.owned("msla",org,id);require("PROPOSED".equals(a.get("status")),"Agreement is not awaiting a decision");
                db.update("update msla set status=? where id=?",event.type().equals("AGREEMENT_ACCEPTED")?"ACTIVE":"REJECTED",id);
            }
            case "LOCATE_APPROVED","LOCATE_REJECTED","LOCATE_EXPIRED" -> locate(org,id,event.type(),d);
            case "RESERVATION_CONFIRMED","RESERVATION_REJECTED","RESERVATION_RELEASED","RESERVATION_EXPIRED","RESERVATION_UPDATED" -> reservation(org,id,event.type(),d);
            case "LOAN_SETTLED" -> settled(org,id,event.occurredAt());
            case "LOAN_REJECTED" -> loanRejected(org,id);
            case "RETURN_SETTLED","RETURN_REJECTED" -> returned(org,id,event.type().equals("RETURN_SETTLED"));
            case "COLLATERAL_PLEDGED" -> collateral.applyPledgeConfirmation(org,id);
            case "COLLATERAL_REJECTED" -> collateral.applyReleaseConfirmation(org,id,true);
            case "COLLATERAL_RELEASED" -> collateral.applyReleaseConfirmation(org,id,false);
            case "COLLATERAL_RELEASE_REJECTED" -> {
                var p=db.owned("collateral_position",org,id);require("RELEASE_REQUESTED".equals(p.get("status")),"Collateral release is not pending");
                db.update("update collateral_position set status='POSTED' where id=?",id);
            }
            case "PAYMENT_CONFIRMED","PAYMENT_REJECTED" -> payments.applyConfirmation(org,id,event.type().equals("PAYMENT_CONFIRMED"),decimal(d,"amount"));
            case "FEE_BILL_ISSUED" -> fee(org,id,d);
            case "ACCRUAL_REPORTED" -> accrual(event);
            case "REBATE_ACCRUED" -> rebate(org,id,d);
            case "REBATE_PAID" -> rebatePaid(org,id,d);
            case "VALUATION_REPORTED" -> valuation(event);
            case "MARGIN_CALL_UPDATED" -> margin(org,id,d);
            case "RECALL_UPDATED" -> recall(org,id,d);
            case "DEFAULT_REPORTED" -> defaultView(org,id,d);
            case "BUY_IN_REPORTED" -> buyInView(org,id,d);
            case "LOAN_STATUS_UPDATED" -> {
                var loan=db.owned("loan",org,id);String status=text(d,"status");
                require(Set.of("DEFAULTED","RECOVERED","CLOSED").contains(status),"Unsupported externally controlled loan status");
                long outstanding=number(d,"outstandingQuantity");require(outstanding>=0 && outstanding<=n(loan,"outstanding_quantity"),"Invalid outstanding quantity");
                db.update("update loan set status=?,outstanding_quantity=? where id=?",status,outstanding,id);
            }
            case "HOLDING_IMPORTED" -> {
                db.one("select id from security_master where id=?",id);db.holding(org,id);
                long owned=number(d,"ownedQuantity"),borrowed=number(d,"borrowedQuantity");require(owned>=0 && borrowed>=0,"Invalid holding quantities");
                db.update("update holdings set owned_quantity=?,borrowed_quantity=?,average_cost=? where organization_id=? and security_id=?",owned,borrowed,decimal(d,"averageCost"),org,id);
            }
            default -> throw new IllegalArgumentException("Unsupported borrower confirmation "+event.type());
        }
        if(cursor.isEmpty())db.update("insert into projection_cursor(resource,reference_id,version) values (?,?,?)",resource,id,event.version());
        else db.update("update projection_cursor set version=? where resource=? and reference_id=?",event.version(),resource,id);
        db.update("insert into inbox_event(event_id) values (?)",event.eventId());
        events.write(org,"BORROWER_VIEW_UPDATED",id,Map.of("sourceEventId",event.eventId(),"sourceType",event.type()));
        if(Set.of("MARGIN_CALL_UPDATED","RECALL_UPDATED","DEFAULT_REPORTED","BUY_IN_REPORTED","LOAN_SETTLED","RETURN_SETTLED","REBATE_PAID").contains(event.type()))
            db.insert("insert into notification(organization_id,type,reference,message) values (?,?,?,?)",org,event.type(),String.valueOf(id),event.type().replace('_',' '));
        return Map.of("applied",true);
    }
    private String resource(String type) {
        if(type.startsWith("AGREEMENT_"))return "msla";
        if(type.startsWith("LOCATE_"))return "locate_request";
        if(type.startsWith("RESERVATION_"))return "reservation";
        if(type.startsWith("LOAN_"))return "loan";
        if(type.startsWith("RETURN_"))return "loan_return";
        if(type.startsWith("COLLATERAL_"))return "collateral_position";
        if(type.startsWith("PAYMENT_"))return "payment_request";
        if(type.startsWith("REBATE_"))return "rebate";
        return type;
    }
    private void lender(long id,Map<String,Object> d) {
        if(db.list("select id from lender_reference where id=?",id).isEmpty())
            db.update("insert into lender_reference(id,name,external_id) values (?,?,?)",id,text(d,"name"),text(d,"externalId"));
        else db.update("update lender_reference set name=?,external_id=? where id=?",text(d,"name"),text(d,"externalId"),id);
    }
    private void locate(long org,long id,String type,Map<String,Object> d) {
        var l=db.owned("locate_request",org,id);
        if(type.equals("LOCATE_APPROVED")) {
            require("PENDING".equals(l.get("status")),"Locate is not pending");long quantity=number(d,"approvedQuantity");
            require(quantity>0 && quantity<=n(l,"quantity"),"Invalid approved quantity");
            db.update("update locate_request set status=?,approved_quantity=? where id=?",quantity==n(l,"quantity")?"APPROVED":"PARTIALLY_APPROVED",quantity,id);
            // An approved locate immediately creates a borrower-side reservation request.
            // The lender confirms it and reserves inventory through the normal command flow.
            if(db.list("select id from reservation where locate_id=?",id).isEmpty()) {
                long reservationId=db.insert(
                    "insert into reservation(organization_id,locate_id,msla_id,security_id,quantity,remaining_quantity,status,expires_at) values (?,?,?,?,?,0,'REQUESTED',?)",
                    org,id,n(l,"msla_id"),n(l,"security_id"),quantity,instant(l,"expires_at"));
                events.write(org,"RESERVATION_REQUESTED",reservationId,
                    Map.of("locateId",id,"quantity",quantity,"automatic",true));
            }
        } else db.update("update locate_request set status=? where id=?",type.equals("LOCATE_REJECTED")?"REJECTED":"EXPIRED",id);
    }
    private void reservation(long org,long id,String type,Map<String,Object> d) {
        var r=db.owned("reservation",org,id);
        if(type.equals("RESERVATION_UPDATED")) {
            long remaining=number(d,"remainingQuantity");String status=text(d,"status");
            require(remaining>=0 && remaining<=n(r,"quantity") && Set.of("ACTIVE","CONSUMED","EXPIRED","RELEASED").contains(status),"Invalid authoritative reservation state");
            db.update("update reservation set remaining_quantity=?,status=? where id=?",remaining,status,id);return;
        }
        if(type.equals("RESERVATION_CONFIRMED")) {
            require("REQUESTED".equals(r.get("status")),"Reservation is not awaiting confirmation");
            long quantity=number(d,"quantity");require(quantity>0 && quantity<=n(r,"quantity"),"Invalid reserved quantity");
            db.update("update reservation set quantity=?,remaining_quantity=?,status='ACTIVE' where id=?",quantity,quantity,id);
        } else {
            String status=type.equals("RESERVATION_RELEASED")?"RELEASED":type.equals("RESERVATION_EXPIRED")?"EXPIRED":"REJECTED";
            db.update("update reservation set status=?,remaining_quantity=0 where id=?",status,id);
        }
    }
    private void settled(long org,long id,Instant at) {
        var loan=db.owned("loan",org,id);require("PENDING_SETTLEMENT".equals(loan.get("status")),"Loan is not awaiting settlement");
        db.holding(org,n(loan,"security_id"));
        db.update("update holdings set borrowed_quantity=borrowed_quantity+? where organization_id=? and security_id=?",n(loan,"quantity"),org,n(loan,"security_id"));
        db.update("update loan set status='ACTIVE',settled_at=?,settled_on=? where id=?",OffsetDateTime.ofInstant(at,ZoneOffset.UTC),at.atZone(ZoneId.of("Asia/Kolkata")).toLocalDate(),id);
    }
    private void loanRejected(long org,long id) {
        var loan=db.owned("loan",org,id);require("PENDING_SETTLEMENT".equals(loan.get("status")),"Only unsettled loans can be rejected");
        for(var p:db.list("select * from collateral_position where loan_id=? and status='PLEDGE_PENDING'",id))collateral.applyReleaseConfirmation(org,n(p,"id"),true);
        // Remote reservation restoration is a separate authoritative event, not a local expiry/unlock decision.
        db.update("update loan set status='REJECTED',outstanding_quantity=0 where id=?",id);
    }
    private void returned(long org,long id,boolean settled) {
        var r=db.owned("loan_return",org,id);require("PENDING".equals(r.get("status")),"Return is not pending");
        var loan=db.owned("loan",org,n(r,"loan_id"));
        if(settled) {
            long remaining=n(loan,"outstanding_quantity")-n(r,"quantity");require(remaining>=0,"Return exceeds outstanding quantity");
            db.update("update loan set outstanding_quantity=? where id=?",remaining,n(loan,"id"));
            db.update("update loan_return set status='SETTLED',settled_at=current_timestamp where id=?",id);
            if(remaining==0)db.update("update loan set status='CLOSED',closed_at=current_timestamp where id=?",n(loan,"id"));
        } else {
            var h=db.one("select * from holdings where organization_id=? and security_id=?",org,n(loan,"security_id"));
            long old=n(h,"owned_quantity"),restore=n(r,"owned_quantity"),total=old+restore;
            BigDecimal cost=total==0?BigDecimal.ZERO:money(h,"average_cost").multiply(BigDecimal.valueOf(old)).add(money(r,"owned_cost").multiply(BigDecimal.valueOf(restore))).divide(BigDecimal.valueOf(total),6,RoundingMode.HALF_UP);
            db.update("update holdings set owned_quantity=owned_quantity+?,borrowed_quantity=borrowed_quantity+?,average_cost=? where organization_id=? and security_id=?",restore,n(r,"borrowed_quantity"),cost,org,n(loan,"security_id"));
            db.update("update loan_return set status='REJECTED' where id=?",id);
        }
        if(n(db.owned("loan",org,n(loan,"id")),"outstanding_quantity")>0 && db.list("select id from loan_return where loan_id=? and status='PENDING'",n(loan,"id")).isEmpty())
            db.update("update loan set status='ACTIVE' where id=? and status='RETURN_PENDING'",n(loan,"id"));
    }
    private void fee(long org,long id,Map<String,Object> d) {
        db.owned("loan",org,number(d,"loanId"));BigDecimal value=decimal(d,"amount");require(value.signum()>0,"Fee bill must be positive");
        require(db.list("select id from lending_fee where id=?",id).isEmpty(),"Fee bill already exists; issue a new adjustment bill");
        db.update("insert into lending_fee(id,organization_id,loan_id,period_start,period_end,fee_rate,fee_amount,due_date,status) values (?,?,?,?,?,?,?,?,'DUE')",id,org,number(d,"loanId"),dateValue(d,"periodStart"),dateValue(d,"periodEnd"),decimal(d,"rate"),value,dateValue(d,"dueDate"));
    }
    private void accrual(BorrowerEvent e) {
        var d=e.data();db.owned("loan",e.organizationId(),number(d,"loanId"));String type=text(d,"type");require(Set.of("FEE","REBATE").contains(type),"Invalid accrual type");
        db.update("insert into fee_accrual_view(id,organization_id,loan_id,accrual_date,type,amount,base_value,rate,source_event_id) values (?,?,?,?,?,?,?,?,?)",e.referenceId(),e.organizationId(),number(d,"loanId"),dateValue(d,"date"),type,decimal(d,"amount"),decimal(d,"baseValue"),decimal(d,"rate"),e.eventId());
    }
    private void rebate(long org,long id,Map<String,Object> d) {
        db.owned("loan",org,number(d,"loanId"));
        require(db.list("select id from rebate where id=?",id).isEmpty(),"Rebate accrual already exists");
        db.update("insert into rebate(id,organization_id,loan_id,rebate_rate,amount,accrual_date,next_payment_date,status) values (?,?,?,?,?,?,?,'ACCRUED')",id,org,number(d,"loanId"),decimal(d,"rate"),decimal(d,"amount"),dateValue(d,"date"),dateValue(d,"paymentDate"));
    }
    private void rebatePaid(long org,long id,Map<String,Object> d) {
        var r=db.owned("rebate",org,id);require("ACCRUED".equals(r.get("status")),"Rebate is not awaiting payment");BigDecimal value=decimal(d,"amount");
        require(value.compareTo(money(r,"amount"))==0,"Rebate payment does not match reported accrual");
        db.insert("insert into rebate_payment(organization_id,loan_id,rebate_id,amount) values (?,?,?,?)",org,n(r,"loan_id"),id,value);
        accounts.changeCash(org,value,"REBATE_RECEIVED",String.valueOf(id));db.update("update rebate set status='PAID' where id=?",id);
    }
    private void valuation(BorrowerEvent e) {
        var d=e.data();db.owned("loan",e.organizationId(),number(d,"loanId"));
        db.insert("insert into loan_valuation(organization_id,loan_id,valued_at,loan_market_value,required_collateral,recognized_collateral,coverage_ratio,shortfall,source_event_id) values (?,?,?,?,?,?,?,?,?)",e.organizationId(),number(d,"loanId"),e.occurredAt(),decimal(d,"loanMarketValue"),decimal(d,"requiredCollateral"),decimal(d,"recognizedCollateral"),decimal(d,"coverageRatio"),decimal(d,"shortfall"),e.eventId());
    }
    private void margin(long org,long id,Map<String,Object> d) {
        long loan=number(d,"loanId");db.owned("loan",org,loan);String status=obligationStatus(d);
        if(db.list("select id from margin_recall where id=?",id).isEmpty())
            db.update("insert into margin_recall(id,organization_id,loan_id,external_id,amount,fulfilled_amount,reason,status,due_at) values (?,?,?,?,?,?,?,?,?)",id,org,loan,text(d,"externalId"),decimal(d,"amount"),decimal(d,"fulfilledAmount"),text(d,"reason"),status,instantValue(d,"dueAt"));
        else {db.owned("margin_recall",org,id);db.update("update margin_recall set amount=?,fulfilled_amount=?,status=?,due_at=? where id=?",decimal(d,"amount"),decimal(d,"fulfilledAmount"),status,instantValue(d,"dueAt"),id);}
    }
    private void recall(long org,long id,Map<String,Object> d) {
        long loan=number(d,"loanId");db.owned("loan",org,loan);String status=obligationStatus(d);
        if(db.list("select id from recall where id=?",id).isEmpty())
            db.update("insert into recall(id,organization_id,loan_id,external_id,quantity,fulfilled_quantity,reason,status,due_at) values (?,?,?,?,?,?,?,?,?)",id,org,loan,text(d,"externalId"),number(d,"quantity"),number(d,"fulfilledQuantity"),text(d,"reason"),status,instantValue(d,"dueAt"));
        else {db.owned("recall",org,id);db.update("update recall set quantity=?,fulfilled_quantity=?,status=?,due_at=? where id=?",number(d,"quantity"),number(d,"fulfilledQuantity"),status,instantValue(d,"dueAt"),id);}
    }
    private void defaultView(long org,long id,Map<String,Object> d) {
        long loan=number(d,"loanId");db.owned("loan",org,loan);
        if(db.list("select id from loan_default where id=?",id).isEmpty())
            db.update("insert into loan_default(id,organization_id,loan_id,obligation_id,obligation_type,reason,status) values (?,?,?,?,?,?,?)",id,org,loan,number(d,"obligationId"),text(d,"obligationType"),text(d,"reason"),text(d,"status"));
        else {db.owned("loan_default",org,id);db.update("update loan_default set status=? where id=?",text(d,"status"),id);}
    }
    private void buyInView(long org,long id,Map<String,Object> d) {
        db.owned("loan_default",org,number(d,"defaultId"));require(db.list("select id from buy_in where id=?",id).isEmpty(),"Buy-in projection already exists");
        db.update("insert into buy_in(id,organization_id,default_id,quantity,price,costs,collateral_applied,residual_balance,status) values (?,?,?,?,?,?,?,?,?)",id,org,number(d,"defaultId"),number(d,"quantity"),decimal(d,"price"),decimal(d,"costs"),decimal(d,"collateralApplied"),decimal(d,"residualBalance"),text(d,"status"));
    }
    private static String obligationStatus(Map<String,Object> d) {String s=text(d,"status");require(Set.of("OPEN","PARTIALLY_FULFILLED","FULFILLED","OVERDUE","CANCELLED").contains(s),"Invalid obligation status");return s;}
    private static String text(Map<String,Object> d,String key) {Object v=d.get(key);require(v!=null && !v.toString().isBlank(),"Missing event field "+key);return v.toString();}
    private static long number(Map<String,Object> d,String key) {return new BigDecimal(text(d,key)).longValueExact();}
    private static BigDecimal decimal(Map<String,Object> d,String key) {BigDecimal value=new BigDecimal(text(d,key));require(value.signum()>=0,"Negative event amount "+key);return value;}
    private static LocalDate dateValue(Map<String,Object> d,String key) {return LocalDate.parse(text(d,key));}
    private static OffsetDateTime instantValue(Map<String,Object> d,String key) {return OffsetDateTime.ofInstant(Instant.parse(text(d,key)),ZoneOffset.UTC);}
}
