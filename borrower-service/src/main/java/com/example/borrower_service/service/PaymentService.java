package com.example.borrower_service.service;

import com.example.borrower_service.dto.FundingRequest;
import com.example.borrower_service.repository.BorrowerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import static com.example.borrower_service.repository.BorrowerRepository.*;

/** Borrower initiates payments. Only external confirmations settle them. No fee/rebate calculations live here. */
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentService {
    private final BorrowerRepository db;private final DirectoryService accounts;private final EventWriter events;
    public Map<String,Object> funding(long org,FundingRequest r,boolean deposit) {
        return request(org,deposit?"DEPOSIT":"WITHDRAWAL",r.amount(),r.clientRequestId(),null);
    }
    public Map<String,Object> payFee(long org,long feeId,String key) {
        db.lock(org);var fee=db.owned("lending_fee",org,feeId);
        // A client retry returns the original pending/confirmed payment rather than charging again.
        var retry=db.list("select * from payment_request where organization_id=? and client_request_id=?",org,key);
        if(!retry.isEmpty()) {
            var old=retry.get(0);require(old.get("fee_id")!=null && n(old,"fee_id")==feeId,"Idempotency key was used for a different payment");return old;
        }
        require("DUE".equals(fee.get("status")),"Only an externally issued, unpaid bill can be paid");
        require(db.list("select id from payment_request where fee_id=? and status='PENDING'",feeId).isEmpty(),"Fee payment already pending");
        return request(org,"FEE",money(fee,"fee_amount"),key,feeId);
    }
    private Map<String,Object> request(long org,String type,BigDecimal value,String key,Long fee) {
        db.lock(org);require(key!=null && !key.isBlank() && key.length()<=120,"A client request ID of at most 120 characters is required");
        var retry=db.list("select * from payment_request where organization_id=? and client_request_id=?",org,key);
        if(!retry.isEmpty()) {
            var old=retry.get(0);require(type.equals(old.get("type")) && value.compareTo(money(old,"amount"))==0,"Idempotency key was used with different payment details");return old;
        }
        require(value.signum()>0,"Payment amount must be positive");
        long id=db.insert("insert into payment_request(organization_id,fee_id,type,amount,client_request_id,status) values (?,?,?,?,?,'PENDING')",org,fee,type,value,key);
        if(!"DEPOSIT".equals(type)) {
            accounts.changeCash(org,value.negate(),"PAYMENT_CASH_BLOCK",String.valueOf(id));
            db.update("update trader_account set blocked=blocked+? where organization_id=?",value,org);
        }
        events.write(org,type+"_REQUESTED",id,Map.of("amount",value,"clientRequestId",key,"feeId",fee==null?0:fee));
        return db.owned("payment_request",org,id);
    }
    void applyConfirmation(long org,long id,boolean confirmed,BigDecimal actualAmount) {
        var request=db.owned("payment_request",org,id);require("PENDING".equals(request.get("status")),"Payment is no longer pending");
        BigDecimal value=money(request,"amount");require(actualAmount!=null && actualAmount.compareTo(value)==0,"Confirmation amount does not match payment request");
        if("DEPOSIT".equals(request.get("type"))) {
            if(confirmed)accounts.changeCash(org,value,"DEPOSIT_CONFIRMED",String.valueOf(id));
        } else {
            db.update("update trader_account set blocked=blocked-? where organization_id=?",value,org);
            if(!confirmed)accounts.changeCash(org,value,"PAYMENT_REFUNDED",String.valueOf(id));
            if(confirmed && "FEE".equals(request.get("type")))db.update("update lending_fee set status='PAID',paid_at=current_timestamp where id=?",n(request,"fee_id"));
        }
        db.update("update payment_request set status=?,confirmed_at=current_timestamp where id=?",confirmed?"CONFIRMED":"REJECTED",id);
    }
}
