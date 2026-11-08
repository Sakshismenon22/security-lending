package com.example.borrower_service.service;

import com.example.borrower_service.repository.BorrowerRepository;
import com.example.borrower_service.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.util.*;
import static com.example.borrower_service.repository.BorrowerRepository.*;

/** Paper execution against the delayed completed-minute close; no exchange execution claims. */
@Service
@Transactional
public class TradingService {
    private final BorrowerRepository db; private final MarketService market; private final DirectoryService accounts; private final EventWriter events;
    public TradingService(BorrowerRepository db,MarketService market,DirectoryService accounts,EventWriter events) {
        this.db=db; this.market=market; this.accounts=accounts; this.events=events;
    }
    public Map<String,Object> order(long org,OrderRequest r) {
        db.lock(org); var quote=market.price(r.securityId());
        require(!"LIMIT".equals(r.type()) || r.limitPrice()!=null,"Limit price is required");
        require(!"MARKET".equals(r.type()) || r.limitPrice()==null,"Market orders must omit limitPrice");
        db.holding(org,r.securityId());
        var h=db.one("select * from holdings where organization_id=? and security_id=?",org,r.securityId());
        BigDecimal reserve=BigDecimal.ZERO;
        if("BUY".equals(r.side())) {
            if("BORROWED".equals(r.source())) {
                long shorts=shortQuantity(org,r.securityId());
                long pending=((Number)db.one("select coalesce(sum(quantity),0) as qty from trade_order where organization_id=? and security_id=? and side='BUY' and source='BORROWED' and status='PENDING'",org,r.securityId()).get("qty")).longValue();
                require(r.quantity()<=shorts-pending,"Buy-to-cover quantity exceeds the uncovered short position");
            }
            reserve=amount(("LIMIT".equals(r.type())?r.limitPrice():quote.value()).multiply(BigDecimal.valueOf(r.quantity())));
            accounts.changeCash(org,reserve.negate(),"ORDER_CASH_BLOCK","new-order");
            db.update("update trader_account set blocked=blocked+? where organization_id=?",reserve,org);
        } else if("OWNED".equals(r.source())) {
            require(n(h,"owned_quantity")-n(h,"pledged_quantity")-n(h,"blocked_owned")>=r.quantity(),"Insufficient unpledged owned securities");
            db.update("update holdings set blocked_owned=blocked_owned+? where organization_id=? and security_id=?",r.quantity(),org,r.securityId());
        } else {
            require(n(h,"borrowed_quantity")-n(h,"blocked_borrowed")>=r.quantity(),"Insufficient settled borrowed securities; locate approval is not sellable inventory");
            db.update("update holdings set blocked_borrowed=blocked_borrowed+? where organization_id=? and security_id=?",r.quantity(),org,r.securityId());
        }
        long id=db.insert("insert into trade_order(organization_id,security_id,side,type,source,quantity,limit_price,reserved_cash,status) values (?,?,?,?,?,?,?,?,'PENDING')",org,r.securityId(),r.side(),r.type(),r.source(),r.quantity(),r.limitPrice(),reserve);
        events.write(org,"ORDER_SUBMITTED",id,r);
        match(org,id); return db.owned("trade_order",org,id);
    }
    public Map<String,Object> cancel(long org,long id) {
        db.lock(org); var order=db.owned("trade_order",org,id); require("PENDING".equals(order.get("status")),"Only pending orders can be cancelled");
        release(order); db.update("update trade_order set status='CANCELLED' where id=?",id);
        events.write(org,"ORDER_CANCELLED",id,Map.of()); return db.owned("trade_order",org,id);
    }
    private void release(Map<String,Object> order) {
        long org=n(order,"organization_id"),s=n(order,"security_id"),q=n(order,"quantity");
        if("BUY".equals(order.get("side"))) {
            BigDecimal reserved=money(order,"reserved_cash");
            db.update("update trader_account set blocked=blocked-? where organization_id=?",reserved,org);
            accounts.changeCash(org,reserved,"ORDER_CASH_RELEASE",String.valueOf(order.get("id")));
        } else {
            String column="OWNED".equals(order.get("source"))?"blocked_owned":"blocked_borrowed";
            db.update("update holdings set "+column+"="+column+"-? where organization_id=? and security_id=?",q,org,s);
        }
    }
    public void match(long org,long id) {
        db.lock(org); var order=db.owned("trade_order",org,id); if(!"PENDING".equals(order.get("status"))) return;
        long security=n(order,"security_id"),q=n(order,"quantity");
        var quote=market.price(security); boolean buy="BUY".equals(order.get("side"));
        if("LIMIT".equals(order.get("type"))) {
            int cmp=quote.value().compareTo(money(order,"limit_price"));
            if(buy?cmp>0:cmp<0) return;
        }
        var h=db.one("select * from holdings where organization_id=? and security_id=?",org,security);
        BigDecimal value=amount(quote.value().multiply(BigDecimal.valueOf(q)));
        release(order);
        if(buy) {
            accounts.changeCash(org,value.negate(),"TRADE_BUY",String.valueOf(id));
            long owned=n(h,"owned_quantity"),total=Math.addExact(owned,q);
            BigDecimal average=money(h,"average_cost").multiply(BigDecimal.valueOf(owned)).add(value).divide(BigDecimal.valueOf(total),6,RoundingMode.HALF_UP);
            db.update("update holdings set owned_quantity=?,average_cost=? where organization_id=? and security_id=?",total,average,org,security);
            if("BORROWED".equals(order.get("source"))) cover(org,security,q,quote.value());
        } else {
            accounts.changeCash(org,value,"TRADE_SELL",String.valueOf(id));
            if("OWNED".equals(order.get("source"))) {
                BigDecimal pnl=amount(quote.value().subtract(money(h,"average_cost")).multiply(BigDecimal.valueOf(q)));
                db.update("update holdings set owned_quantity=owned_quantity-?,realized_pnl=realized_pnl+? where organization_id=? and security_id=?",q,pnl,org,security);
            } else {
                db.update("update holdings set borrowed_quantity=borrowed_quantity-? where organization_id=? and security_id=?",q,org,security);
                db.insert("insert into short_lot(organization_id,security_id,order_id,open_quantity,price) values (?,?,?,?,?)",org,security,id,q,quote.value());
            }
        }
        db.insert("insert into trade_history(organization_id,order_id,security_id,side,source,quantity,price,value,price_at) values (?,?,?,?,?,?,?,?,?)",org,id,security,order.get("side"),order.get("source"),q,quote.value(),value,quote.at());
        db.update("update trade_order set status='FILLED',filled_at=current_timestamp where id=?",id);
        events.write(org,"ORDER_FILLED",id,Map.of("executionPrice",quote.value(),"quantity",q,"simulated",true));
    }
    private long shortQuantity(long org,long security) {
        return ((Number)db.one("select coalesce(sum(open_quantity),0) as qty from short_lot where organization_id=? and security_id=?",org,security).get("qty")).longValue();
    }
    private void cover(long org,long security,long q,BigDecimal price) {
        require(shortQuantity(org,security)>=q,"Short position is no longer available to cover");
        long remaining=q; BigDecimal pnl=BigDecimal.ZERO;
        for(var lot:db.list("select * from short_lot where organization_id=? and security_id=? and open_quantity>0 order by id",org,security)) {
            long fill=Math.min(remaining,n(lot,"open_quantity"));
            pnl=pnl.add(money(lot,"price").subtract(price).multiply(BigDecimal.valueOf(fill)));
            db.update("update short_lot set open_quantity=open_quantity-? where id=?",fill,n(lot,"id")); remaining-=fill;
            if(remaining==0)break;
        }
        db.update("update holdings set realized_pnl=realized_pnl+? where organization_id=? and security_id=?",amount(pnl),org,security);
    }
}
