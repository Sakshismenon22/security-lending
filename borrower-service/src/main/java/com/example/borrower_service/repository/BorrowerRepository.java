package com.example.borrower_service.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.sql.Statement;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** SQL is explicit so the reservation, cash and quantity invariants are reviewable. */
@Repository
public class BorrowerRepository {
    public final JdbcTemplate jdbc;
    public BorrowerRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public List<Map<String,Object>> list(String sql, Object... args) { return jdbc.queryForList(sql,args); }
    public Map<String,Object> one(String sql,Object... args) {
        var rows=list(sql,args);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Record not found");
        return rows.get(0);
    }
    public Map<String,Object> owned(String table,long org,long id) {
        if(!Set.of("msla","locate_request","reservation","loan","collateral_position","loan_return","margin_recall","recall","trade_order","loan_default","users","lending_fee","rebate","notification","payment_request").contains(table)) throw new IllegalArgumentException("Unknown table");
        return one("select * from "+table+" where organization_id=? and id=?",org,id);
    }
    public int update(String sql,Object... args) { return jdbc.update(sql,args); }
    public long insert(String sql,Object... args) {
        var key=new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var ps=connection.prepareStatement(sql,new String[]{"id"});
            for(int i=0;i<args.length;i++) ps.setObject(i+1,args[i] instanceof Instant t ? OffsetDateTime.ofInstant(t,ZoneOffset.UTC):args[i]);
            return ps;
        },key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }
    /** All financial writes for one organization serialize on this row, including callbacks/jobs. */
    public void lock(long org) { one("select id from organization where id=? for update",org); }
    public void holding(long org,long security) {
        if(list("select security_id from holdings where organization_id=? and security_id=?",org,security).isEmpty())
            update("insert into holdings(organization_id,security_id) values (?,?)",org,security);
    }
    public static long n(Map<String,Object> row,String key) { return ((Number)row.get(key)).longValue(); }
    public static BigDecimal money(Map<String,Object> row,String key) { return new BigDecimal(row.get(key).toString()); }
    public static Instant instant(Map<String,Object> row,String key) {
        Object v=row.get(key);
        if(v instanceof OffsetDateTime d) return d.toInstant();
        if(v instanceof java.sql.Timestamp d) return d.toInstant();
        return Instant.parse(v.toString());
    }
    public static LocalDate date(Map<String,Object> row,String key) { return LocalDate.parse(row.get(key).toString()); }
    public static void require(boolean condition,String message) {
        if(!condition) throw new ResponseStatusException(HttpStatus.CONFLICT,message);
    }
    public static BigDecimal amount(BigDecimal value) { return value.setScale(6,java.math.RoundingMode.HALF_UP); }
}
