package com.example.borrower_service.service;

import com.example.borrower_service.repository.BorrowerRepository;
import com.example.borrower_service.dto.AllocationRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import static com.example.borrower_service.repository.BorrowerRepository.*;

@Service
@Transactional
public class CollateralService {
    private final BorrowerRepository db; private final MarketService market; private final DirectoryService accounts;
    private final EventWriter events;
    public CollateralService(BorrowerRepository db,MarketService market,DirectoryService accounts,EventWriter events) {
        this.db=db; this.market=market; this.accounts=accounts; this.events=events;
    }
    public Map<String,Object> post(long org,long loanId,AllocationRequest r) {
        db.lock(org); var loan=db.owned("loan",org,loanId);
        require(Set.of("PENDING_SETTLEMENT","ACTIVE","RETURN_PENDING").contains(loan.get("status")),"Collateral cannot be added in this loan state");
        if(r.marginCallId()!=null) {
            var call=db.owned("margin_recall",org,r.marginCallId());
            require(n(call,"loan_id")==loanId && Set.of("OPEN","PARTIALLY_FULFILLED","OVERDUE").contains(call.get("status")),"Margin call does not belong to this loan or is already resolved");
        }
        boolean cash="CASH".equals(r.assetType());
        require(cash?(r.securityId()==null && r.quantity()==0 && r.cashAmount().signum()>0):(r.securityId()!=null && r.quantity()>0 && r.cashAmount().signum()==0),"Cash requires an amount only; securities require securityId and quantity only");
        var rules=cash?db.list("select * from collateral_haircut where msla_id=? and asset_type='CASH'",n(loan,"msla_id")):
            db.list("select * from collateral_haircut where msla_id=? and asset_type='SECURITY' and security_id=?",n(loan,"msla_id"),r.securityId());
        require(!rules.isEmpty(),"Collateral is not eligible under the MSLA");
        if(cash) {
            accounts.changeCash(org,r.cashAmount().negate(),"COLLATERAL_PLEDGED",String.valueOf(loanId));
            db.update("update trader_account set pledged=pledged+? where organization_id=?",r.cashAmount(),org);
        } else {
            market.price(r.securityId()); db.holding(org,r.securityId());
            var h=db.one("select * from holdings where organization_id=? and security_id=?",org,r.securityId());
            require(n(h,"owned_quantity")-n(h,"pledged_quantity")-n(h,"blocked_owned")>=r.quantity(),"Insufficient owned securities available for collateral");
            db.update("update holdings set pledged_quantity=pledged_quantity+? where organization_id=? and security_id=?",r.quantity(),org,r.securityId());
        }
        long asset=n(db.one("select id from assets where asset_name=?",r.assetType()),"id");
        long id=db.insert("insert into collateral_position(organization_id,loan_id,asset_type,asset_id,security_id,quantity,cash_amount,haircut,status,margin_call_id) values (?,?,?,?,?,?,?,?,'PLEDGE_PENDING',?)",org,loanId,r.assetType(),asset,r.securityId(),r.quantity(),r.cashAmount(),money(rules.get(0),"haircut"),r.marginCallId());
        movement(org,loanId,id,"PLEDGE_REQUESTED",r.cashAmount(),r.quantity(),asset,r.securityId());
        events.write(org,"COLLATERAL_PLEDGE_REQUESTED",loanId,Map.of("positionId",id,"allocation",r));
        return db.owned("collateral_position",org,id);
    }
    public Map<String,Object> respondToMarginCall(long org,long callId,AllocationRequest r) {
        db.lock(org);var call=db.owned("margin_recall",org,callId);
        require(r.marginCallId()==null || r.marginCallId()==callId,"Margin call reference does not match the request path");
        return post(org,n(call,"loan_id"),new AllocationRequest(r.assetType(),r.securityId(),r.quantity(),r.cashAmount(),callId));
    }
    void applyPledgeConfirmation(long org,long id) {
        var p=db.owned("collateral_position",org,id);
        require("PLEDGE_PENDING".equals(p.get("status")),"Collateral pledge is not pending");
        db.update("update collateral_position set status='POSTED' where id=?",id);
        movement(org,n(p,"loan_id"),id,"PLEDGE_CONFIRMED",money(p,"cash_amount"),n(p,"quantity"),n(p,"asset_id"),p.get("security_id"));
    }
    /** Submission preview only. End-of-day valuation and margin decisions come from the lifecycle service. */
    public Map<String,Object> preview(long org,long loanId) {
        var loan=db.owned("loan",org,loanId); var p=market.price(n(loan,"security_id"));
        BigDecimal loanValue=amount(p.value().multiply(BigDecimal.valueOf(n(loan,"outstanding_quantity"))));
        BigDecimal required=amount(loanValue.multiply(money(loan,"coverage_ratio"))), recognized=BigDecimal.ZERO,marketValue=BigDecimal.ZERO;
        var positions=new ArrayList<Map<String,Object>>();
        for(var position:db.list("select * from collateral_position where organization_id=? and loan_id=? and status in ('PLEDGE_PENDING','POSTED','RELEASE_REQUESTED')",org,loanId)) {
            BigDecimal value="CASH".equals(position.get("asset_type"))?money(position,"cash_amount"):
                market.price(n(position,"security_id")).value().multiply(BigDecimal.valueOf(n(position,"quantity")));
            BigDecimal recognizedValue=amount(value.multiply(BigDecimal.ONE.subtract(money(position,"haircut"))));
            marketValue=marketValue.add(value); recognized=recognized.add(recognizedValue);
            var row=new LinkedHashMap<>(position); row.put("market_value",amount(value));row.put("recognized_value",recognizedValue);positions.add(row);
        }
        return Map.of("indicative",true,"loanId",loanId,"loanMarketValue",loanValue,"requiredCollateral",required,"marketValue",amount(marketValue),
            "recognizedValue",amount(recognized),"shortfall",amount(required.subtract(recognized).max(BigDecimal.ZERO)),
            "surplus",amount(recognized.subtract(required).max(BigDecimal.ZERO)),"priceAt",p.at(),"positions",positions);
    }
    public void validateInitialAllocation(long org,long loanId) {
        require(((BigDecimal)preview(org,loanId).get("shortfall")).signum()==0,"Insufficient proposed haircut-adjusted collateral");
    }
    public Map<String,Object> requestRelease(long org,long id) {
        db.lock(org);var p=db.owned("collateral_position",org,id);var loan=db.owned("loan",org,n(p,"loan_id"));
        require("POSTED".equals(p.get("status")),"Collateral release is already pending or completed");
        require(!Set.of("DEFAULTED","RECOVERED","REJECTED").contains(loan.get("status")),"Collateral cannot be released in this state");
        db.update("update collateral_position set status='RELEASE_REQUESTED' where id=?",id);
        events.write(org,"COLLATERAL_RELEASE_REQUESTED",id,Map.of("loanId",n(p,"loan_id")));
        return db.owned("collateral_position",org,id);
    }
    void applyReleaseConfirmation(long org,long id,boolean rejectedLoan) {
        var p=db.owned("collateral_position",org,id); long loanId=n(p,"loan_id");
        require(rejectedLoan?"PLEDGE_PENDING".equals(p.get("status")):"RELEASE_REQUESTED".equals(p.get("status")),"No pending pledge or release request exists");
        require(!"RELEASED".equals(p.get("status")),"Collateral already released");
        var loan=db.owned("loan",org,loanId);
        db.update("update collateral_position set status=? where id=?",rejectedLoan?"REJECTED":"RELEASED",id);
        if("CASH".equals(p.get("asset_type"))) {
            db.update("update trader_account set pledged=pledged-? where organization_id=?",money(p,"cash_amount"),org);
            accounts.changeCash(org,money(p,"cash_amount"),"COLLATERAL_RELEASED",String.valueOf(id));
        } else db.update("update holdings set pledged_quantity=pledged_quantity-? where organization_id=? and security_id=?",n(p,"quantity"),org,n(p,"security_id"));
        movement(org,loanId,id,rejectedLoan?"PLEDGE_REJECTED":"RELEASE_CONFIRMED",money(p,"cash_amount"),n(p,"quantity"),n(p,"asset_id"),p.get("security_id"));
        events.write(org,"COLLATERAL_RELEASE_CONFIRMED",id,Map.of("loanId",loanId));
    }
    private void movement(long org,long loan,long position,String type,BigDecimal cash,long qty,long asset,Object security) {
        db.insert("insert into collateral_history(organization_id,loan_id,position_id,type,amount,quantity,asset_id,security_id) values (?,?,?,?,?,?,?,?)",org,loan,position,type,cash,qty,asset,security);
    }
}
