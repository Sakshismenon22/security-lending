package com.example.borrower_service.service;

import com.example.borrower_service.repository.BorrowerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.util.*;
import static com.example.borrower_service.repository.BorrowerRepository.*;

@Service
@Transactional(readOnly=true)
public class PortfolioService {
    private final BorrowerRepository db;private final CollateralService collateral;private final LendingService lending;
    public PortfolioService(BorrowerRepository db,CollateralService collateral,LendingService lending) {this.db=db;this.collateral=collateral;this.lending=lending;}
    public Object account(long org) {return db.one("select * from trader_account where organization_id=?",org);}
    public Object lenders(long org) {db.one("select id from organization where id=?",org);return db.list("select * from lender_reference order by name");}
    public Object holdings(long org) {
        db.one("select id from organization where id=?",org);
        return db.list("select h.*,s.symbol,s.exchange,s.name,s.price,s.price_at, (h.owned_quantity-h.pledged_quantity-h.blocked_owned) as available_owned_quantity, (h.borrowed_quantity-h.blocked_borrowed) as available_borrowed_quantity, (s.price-h.average_cost)*h.owned_quantity as unrealized_pnl from holdings h join security_master s on s.id=h.security_id where h.organization_id=? order by s.symbol",org);
    }
    public Object positions(long org) {
        db.one("select id from organization where id=?",org);
        return db.list("select s.id as security_id,s.symbol,s.exchange,s.price,s.price_at,coalesce(h.owned_quantity,0) as long_quantity,coalesce(h.realized_pnl,0) as realized_pnl,coalesce(l.quantity,0) as short_quantity,l.average_entry,(l.average_entry-s.price)*coalesce(l.quantity,0) as short_unrealized_pnl from security_master s left join holdings h on h.security_id=s.id and h.organization_id=? left join (select security_id,sum(open_quantity) as quantity,sum(open_quantity*price)/nullif(sum(open_quantity),0) as average_entry from short_lot where organization_id=? and open_quantity>0 group by security_id) l on l.security_id=s.id where coalesce(h.owned_quantity,0)>0 or coalesce(l.quantity,0)>0 order by s.symbol",org,org);
    }
    public Object dashboard(long org) {
        var out=new LinkedHashMap<String,Object>();out.put("account",account(org));
        out.put("loans",db.one("select count(*) as active_loans,coalesce(sum(l.outstanding_quantity*s.price),0) as borrowed_market_value from loan l join security_master s on s.id=l.security_id where l.organization_id=? and l.status in ('ACTIVE','RETURN_PENDING','DEFAULTED')",org));
        out.put("fees",db.one("select coalesce(sum(fee_amount),0) as unpaid_fees from lending_fee where organization_id=? and status='DUE'",org));
        out.put("rebates",db.one("select coalesce(sum(amount),0) as rebates_receivable from rebate where organization_id=? and status='ACCRUED'",org));
        out.put("marginCalls",db.list("select * from margin_recall where organization_id=? and status in ('OPEN','PARTIALLY_FULFILLED','OVERDUE') order by due_at",org));
        out.put("recalls",db.list("select * from recall where organization_id=? and status in ('OPEN','PARTIALLY_FULFILLED','OVERDUE') order by due_at",org));
        out.put("reservations",db.list("select * from reservation where organization_id=? and status='ACTIVE' order by expires_at",org));
        out.put("recentActivity",db.list("select * from audit_event where organization_id=? order by id desc limit 20",org));
        out.put("pricePolicy","Delayed completed-minute prices; inspect priceAt before relying on valuations");out.put("simulatedTrading",true);
        return out;
    }
    public Object agreement(long org,long id) {
        var a=new LinkedHashMap<>(db.owned("msla",org,id));
        a.put("collateralRules",db.list("select * from collateral_haircut where msla_id=?",id));
        try {var used=lending.exposure(org,id);a.put("utilizedCredit",used);a.put("availableCredit",money(a,"credit_limit").subtract(used));}
        catch(org.springframework.web.server.ResponseStatusException e){a.put("valuationError",e.getReason());}
        return a;
    }
    public Object loan(long org,long id) {
        var loan=new LinkedHashMap<>(db.owned("loan",org,id));
        for(String table:List.of("collateral_position","loan_return","lending_fee","rebate","margin_recall","recall"))loan.put(table,db.list("select * from "+table+" where organization_id=? and loan_id=? order by id",org,id));
        loan.put("valuation",authoritativeValuation(org,id));
        try {loan.put("allocationPreview",collateral.preview(org,id));}
        catch(org.springframework.web.server.ResponseStatusException e){loan.put("valuationError",e.getReason());}
        return loan;
    }
    public Object authoritativeValuation(long org,long id) {
        db.owned("loan",org,id);
        return db.list("select * from loan_valuation where organization_id=? and loan_id=? order by valued_at desc,id desc limit 1",org,id);
    }
    @Transactional
    public Object watch(long org,long security,boolean add) {
        db.lock(org);db.one("select id from security_master where id=?",security);
        if(add && db.list("select id from watchlist_entry where organization_id=? and security_id=?",org,security).isEmpty())db.insert("insert into watchlist_entry(organization_id,security_id) values (?,?)",org,security);
        if(!add)db.update("delete from watchlist_entry where organization_id=? and security_id=?",org,security);
        return watchlist(org);
    }
    public Object watchlist(long org) {db.one("select id from organization where id=?",org);return db.list("select s.* from watchlist_entry w join security_master s on s.id=w.security_id where w.organization_id=? order by s.symbol",org);}
    @Transactional
    public void markRead(long org,long id) {db.lock(org);db.owned("notification",org,id);db.update("update notification set is_read=true where id=?",id);}
    public String statement(long org) {
        db.one("select id from organization where id=?",org);var out=new StringBuilder("id,type,amount,balance_after,reference,created_at\r\n");
        for(var row:db.list("select * from trader_account_transaction where organization_id=? order by id",org)) {
            out.append(n(row,"id"));
            for(String key:List.of("type","amount","balance_after","reference","created_at")) {
                String value=String.valueOf(row.get(key));
                if(value.matches("^[=+@].*") || (value.startsWith("-") && !value.matches("-?[0-9]+(\\.[0-9]+)?")))value="'"+value;
                out.append(',').append('"').append(value.replace("\"","\"\"")).append('"');
            }
            out.append("\r\n");
        }
        return out.toString();
    }
}
