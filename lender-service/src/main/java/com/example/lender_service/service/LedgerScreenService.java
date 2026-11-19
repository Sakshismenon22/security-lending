package com.example.lender_service.service;

import com.example.lender_service.dto.LedgerSummary;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LedgerScreenService {

  private final TenantService tenants;

  @PersistenceContext
  private EntityManager em;

  private static final String FROM =
    " from LenderAccountLedger e left join Loan l on l.id=e.loanId and l.organizationId=e.organizationId left join BorrowerConnection b on b.id=l.connectionId and b.organizationId=e.organizationId";
  private static final String SELECT =
    "select new com.example.lender_service.dto.LedgerSummary(e.id,e.organizationId,e.type,e.amount,e.reference,e.createdAt,l.id,b.id,b.name,l.borrowerLoanId,e.executionFeeAmount)";

  private String contains(String value) {
    return (
      "%" +
      value
        .trim()
        .toLowerCase(Locale.ROOT)
        .replace("!", "!!")
        .replace("%", "!%")
        .replace("_", "!_") +
      "%"
    );
  }

  public Map<String, Object> overview(
    long org,
    int page,
    int size,
    String account,
    String type,
    Long connectionId,
    Long loanId,
    String q,
    String reference,
    String sort,
    String direction
  ) {
    tenants.exists(org);
    if (
      page < 0 ||
      size < 1 ||
      size > 200 ||
      (long) page * size > Integer.MAX_VALUE
    ) throw new IllegalArgumentException(
      "page must be nonnegative; size must be 1..200"
    );
    var where = new StringBuilder(" where e.organizationId=:org");
    var params = new LinkedHashMap<String, Object>();
    params.put("org", org);
    if (type != null && !type.isBlank()) {
      where.append(" and e.type=:type");
      params.put("type", type);
    }
    if (connectionId != null) {
      where.append(" and b.id=:borrower");
      params.put("borrower", connectionId);
    }
    if (loanId != null) {
      where.append(" and l.id=:loan");
      params.put("loan", loanId);
    }
    if (account != null && !account.isBlank()) {
      where.append(
        switch (account) {
          case "rebate" -> " and upper(e.type) like '%REBATE%'";
          case "fee" -> " and upper(e.type) like '%FEE%' and upper(e.type) not like '%REBATE%'";
          case "buyin" -> " and (upper(e.type) like '%BUY_IN%' or upper(e.type) like '%BUYIN%' or upper(e.type) like '%DEFAULT%') and upper(e.type) not like '%REBATE%' and upper(e.type) not like '%FEE%'";
          case "clearing" -> " and upper(e.type) not like '%REBATE%' and upper(e.type) not like '%FEE%' and upper(e.type) not like '%BUY_IN%' and upper(e.type) not like '%BUYIN%' and upper(e.type) not like '%DEFAULT%'";
          default -> throw new IllegalArgumentException(
            "Unknown ledger account"
          );
        }
      );
    }
    if (reference != null && !reference.isBlank()) {
      where.append(" and lower(e.reference) like :reference escape '!'");
      params.put("reference", contains(reference));
    }
    if (q != null && !q.isBlank()) {
      where.append(
        " and (lower(replace(e.type,'_',' ')) like :q escape '!' or lower(e.reference) like :q escape '!' or lower(b.name) like :q escape '!' or cast(l.id as string) like :q escape '!' or cast(e.id as string) like :q escape '!'"
      );
      params.put("q", contains(q));
      String id = q.trim().toUpperCase(Locale.ROOT);
      if (id.matches("(?:LOAN|LED)-[0-9]+")) {
        try {
          params.put(
            "displayId",
            Long.valueOf(id.substring(id.indexOf('-') + 1))
          );
          where.append(
            id.startsWith("LOAN-")
              ? " or l.id=:displayId"
              : " or e.id=:displayId"
          );
        } catch (NumberFormatException ignored) {
          /* Text search still applies. */
        }
      }
      where.append(")");
    }
    var count = em.createQuery("select count(e)" + FROM + where, Long.class);
    params.forEach(count::setParameter);
    long total = count.getSingleResult();
    var amount = em.createQuery(
      "select coalesce(sum(e.amount),0)" + FROM + where,
      BigDecimal.class
    );
    params.forEach(amount::setParameter);
    String ordering = switch (sort) {
      case "id" -> "e.id";
      case "timestamp" -> "e.createdAt";
      case "type" -> "e.type";
      case "borrower" -> "b.name";
      case "loan" -> "l.id";
      case "debit", "credit" -> "e.amount";
      case "reference" -> "e.reference";
      default -> throw new IllegalArgumentException("Unknown ledger sort");
    };
    if (
      !Set.of("asc", "desc").contains(direction)
    ) throw new IllegalArgumentException("Unknown sort direction");
    var entries = em.createQuery(
      SELECT +
        FROM +
        where +
        " order by " +
        ordering +
        " " +
        direction +
        ", e.id desc",
      LedgerSummary.class
    );
    params.forEach(entries::setParameter);
    entries.setFirstResult(page * size);
    entries.setMaxResults(size);
    // Options cover the entire organization, so filters remain usable on every page.
    var types = em
      .createQuery(
        "select distinct e.type from LenderAccountLedger e where e.organizationId=:org order by e.type",
        String.class
      )
      .setParameter("org", org)
      .getResultList();
    var borrowers = em
      .createQuery(
        "select distinct b.id,b.name" +
          FROM +
          " where e.organizationId=:org and b.id is not null order by b.name",
        Object[].class
      )
      .setParameter("org", org)
      .getResultList()
      .stream()
      .map(row -> Map.of("id", row[0], "name", row[1]))
      .toList();
    var loans = em
      .createQuery(
        "select distinct l.id" +
          FROM +
          " where e.organizationId=:org and l.id is not null order by l.id",
        Long.class
      )
      .setParameter("org", org)
      .getResultList();
    return Map.of(
      "content",
      entries.getResultList(),
      "page",
      page,
      "size",
      size,
      "totalElements",
      total,
      "totalPages",
      (int) ((total + size - 1) / size),
      "totalAmount",
      amount.getSingleResult(),
      "options",
      Map.of("types", types, "borrowers", borrowers, "loans", loans)
    );
  }
}
