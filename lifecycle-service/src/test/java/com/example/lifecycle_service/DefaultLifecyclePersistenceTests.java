package com.example.lifecycle_service;

import static org.junit.jupiter.api.Assertions.*;

import com.example.lifecycle_service.model.*;
import com.example.lifecycle_service.repository.*;
import com.example.lifecycle_service.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
  properties = {
    "spring.datasource.url=jdbc:h2:mem:default-persistence;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "lifecycle.kafka-enabled=false",
    "lifecycle.default-jobs-enabled=false",
    "lifecycle.accrual-jobs-enabled=false",
    "lifecycle.valuation-jobs-enabled=false",
    "lifecycle.margin-check-cron=-",
  }
)
@Transactional
class DefaultLifecyclePersistenceTests {

  @Autowired
  EntityManager em;

  @Autowired
  DefaultLifecycleService defaults;

  @Autowired
  BuyinLifecycleService buyins;

  @Autowired
  DefaultCaseRecordRepository cases;

  @Autowired
  LoanRepository loans;

  @Autowired
  ObjectMapper json;

  @Autowired CashCollateralMovementRepository cashMovements;
  @Autowired DefaultScanService manualScans;

  @Test void buyinCollateralApplicationReducesTheRebateCashBalanceInThePersistedQuery() {
    em.createNativeQuery("insert into collateral_position(id,organization_id,connection_id,msla_id,loan_id,borrower_loan_id,borrower_position_id,asset_id,asset_type,quantity,cash_amount,haircut,status,created_at) values(900,1,1,1,77,1,1,1,'CASH',0,0,0,'APPLIED',:at)").setParameter("at",Instant.parse("2026-11-18T10:00:00Z")).executeUpdate();
    for(int i=0;i<3;i++) em.createNativeQuery("insert into collateral_history(id,position_id,type,cash_amount,created_at) values(:id,900,:type,:amount,:at)").setParameter("id",901+i).setParameter("type",new String[]{"PLEDGE_CONFIRMED","BUY_IN_COST_APPLIED","RELEASE_CONFIRMED"}[i]).setParameter("amount",new java.math.BigDecimal[]{new java.math.BigDecimal("11000"),new java.math.BigDecimal("10005"),new java.math.BigDecimal("995")}[i]).setParameter("at",Instant.parse("2026-11-18T10:00:00Z").plusSeconds(i)).executeUpdate();
    var changes=cashMovements.cashChangesBefore(77,Instant.parse("2026-11-18T11:00:00Z"));assertEquals(3,changes.size());assertEquals(0,new java.math.BigDecimal("-10005").compareTo(changes.get(1).balanceChange()));assertEquals(0,changes.stream().map(com.example.lifecycle_service.dto.CashCollateralChange::balanceChange).reduce(java.math.BigDecimal.ZERO,java.math.BigDecimal::add).signum());
  }

  @Test
  void defaultAndBuyinSurvivePersistenceAndDuplicateCommandDelivery() throws Exception {
    em.createNativeQuery(
      "insert into organization(id) values(1)"
    ).executeUpdate();
    var loan = new Loan();
    loan.setOrganizationId(1L);
    loan.setConnectionId(2L);
    loan.setMslaId(3L);
    loan.setReservationId(4L);
    loan.setSecurityId(5L);
    loan.setBorrowerLoanId(6L);
    loan.setQuantity(20L);
    loan.setOutstandingQuantity(20L);
    loan.setStatus("ACTIVE");
    loan.setCreatedAt(Instant.now().minusSeconds(100));
    loan.setSettledAt(loan.getCreatedAt());
    loan.setMaturityAt(Instant.now().minusSeconds(1));
    loans.saveAndFlush(loan);
    long loanId = loan.getId();
    manualScans.run(1);
    em.flush();
    em.clear();
    defaults.evaluate(1, loanId);
    var rows = cases.findByOrganizationIdAndLoanId(1L, loanId);
    assertEquals(1, rows.size());
    var c = rows.get(0);
    assertEquals(1, c.getVersion());
    em.createNativeQuery(
      "insert into lifecycle_request(id,organization_id,connection_id,loan_id,type,status,payload) values(100,1,2,:loan,'BUY_IN_REQUESTED','PENDING',:payload)"
    )
      .setParameter("loan", loanId)
      .setParameter("payload", "{\"defaultReferenceId\":" + c.getId() + "}")
      .executeUpdate();
    buyins.process(1, 100);
    buyins.process(1, 100);
    em.flush();
    var events = em
      .createQuery("select e from LenderOutboxEvent e", LenderOutboxEvent.class)
      .getResultList();
    assertEquals(2, events.size());
    em.createNativeQuery(
      "update lifecycle_request set status='ACCEPTED' where id=100"
    ).executeUpdate();
    em.clear();
    String execution =
      "{\"defaultReferenceId\":" +
      c.getId() +
      ",\"buyinRequestId\":100,\"quantity\":20,\"price\":101,\"costs\":5,\"executionReference\":\"TRADE-TEST\"}";
    em.createNativeQuery(
      "insert into lifecycle_request(id,organization_id,connection_id,loan_id,type,status,payload) values(101,1,2,:loan,'BUY_IN_EXECUTION_REQUESTED','PENDING',:payload)"
    )
      .setParameter("loan", loanId)
      .setParameter("payload", execution)
      .executeUpdate();
    buyins.process(1, 101);
    buyins.process(1, 101);
    em.flush();
    events = em
      .createQuery("select e from LenderOutboxEvent e", LenderOutboxEvent.class)
      .getResultList();
    assertEquals(3, events.size());
    var report = events
      .stream()
      .map(e -> {
        try {
          return json.readTree(e.getPayload());
        } catch (Exception x) {
          throw new RuntimeException(x);
        }
      })
      .filter(e -> e.path("type").asText().equals("BUY_IN_REPORTED"))
      .findFirst()
      .orElseThrow();
    assertEquals(
      "TRADE-TEST",
      report.path("data").path("executionReference").asText()
    );
    assertEquals(
      20,
      loans.findById(loanId).orElseThrow().getOutstandingQuantity()
    );
    loans.findById(loanId).orElseThrow().setOutstandingQuantity(0L);
    defaults.evaluate(1, loanId);
    em.flush();
    assertEquals(
      "RESOLVED",
      cases.findById(c.getId()).orElseThrow().getStatus()
    );
  }
}
