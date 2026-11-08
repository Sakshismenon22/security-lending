package com.example.borrower_service.service;

import com.example.borrower_service.repository.BorrowerRepository;
import com.example.borrower_service.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import static com.example.borrower_service.repository.BorrowerRepository.*;

@Service
@Transactional
public class DirectoryService {
    private final BorrowerRepository db; private final EventWriter events;
    public DirectoryService(BorrowerRepository db,EventWriter events) { this.db=db; this.events=events; }
    public Map<String,Object> organization(OrganizationRequest r) {
        long id=db.insert("insert into organization(name,registration_number,currency) values (?,?,?)",r.name(),r.registrationNumber(),r.currency());
        db.update("insert into trader_account(organization_id) values (?)",id);
        events.write(id,"ORGANIZATION_CREATED",id,Map.of("name",r.name()));
        return db.one("select * from organization where id=?",id);
    }
    public Map<String,Object> member(long org,MemberRequest r) {
        db.lock(org);
        if(db.list("select id from users where organization_id=?",org).isEmpty())require("ADMIN".equals(r.role()),"The first organization user must be an administrator");
        long roleId=n(db.one("select id from role where role_name=?",r.role()),"id");
        long id=db.insert("insert into users(organization_id,name,email,role_id) values (?,?,?,?)",org,r.name(),r.email().toLowerCase(Locale.ROOT),roleId);
        events.write(org,"MEMBER_ADDED",id,Map.of("role",r.role())); return db.owned("users",org,id);
    }
    public Map<String,Object> updateMember(long org,long id,MemberUpdateRequest r) {
        db.lock(org); var old=db.owned("users",org,id);
        long admin=n(db.one("select id from role where role_name='ADMIN'"),"id");
        if(n(old,"role_id")==admin && Boolean.TRUE.equals(old.get("active")) && (!r.active() || !"ADMIN".equals(r.role())))
            require(db.list("select id from users where organization_id=? and role_id=? and active=true",org,admin).size()>1,"Cannot remove the last active administrator");
        long roleId=n(db.one("select id from role where role_name=?",r.role()),"id");
        db.update("update users set role_id=?,active=? where id=?",roleId,r.active(),id);
        events.write(org,"MEMBER_UPDATED",id,Map.of("role",r.role(),"active",r.active())); return db.owned("users",org,id);
    }
    public void changeCash(long org,BigDecimal delta,String type,String reference) {
        var account=db.one("select * from trader_account where organization_id=?",org);
        var balance=amount(money(account,"available").add(delta)); require(balance.signum()>=0,"Insufficient available cash");
        db.update("update trader_account set available=? where organization_id=?",balance,org);
        db.insert("insert into trader_account_transaction(organization_id,type,amount,balance_after,reference) values (?,?,?,?,?)",org,type,delta,balance,reference);
        events.write(org,type,reference,Map.of("amount",delta,"available",balance));
    }
}
