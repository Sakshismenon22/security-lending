package com.example.lifecycle_service.service;

import com.example.lifecycle_service.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Validates instructions and records an operator-supplied trade confirmation. No synthetic fills. */
@Service
@RequiredArgsConstructor
public class BuyinLifecycleService {

  private final LoanRepository loans;
  private final DefaultCaseRecordRepository cases;
  private final RecallRequestRecordRepository requests;
  private final LenderOutboxEventRepository outbox;
  private final DefaultLifecycleService defaults;
  private final ObjectMapper json;

  @Transactional(rollbackFor = Exception.class)
  public void process(long org, long id) throws Exception {
    if (loans.lockOrganization(org) == null) throw new IllegalArgumentException(
      "Unknown organization"
    );
    var r = requests.findByIdAndOrganizationId(id, org).orElseThrow();
    if (
      !Set.of("BUY_IN_REQUESTED", "BUY_IN_EXECUTION_REQUESTED").contains(
        r.getType()
      )
    ) return;
    String eventId = "buyin-command-" + id;
    if (
      outbox.existsByEventId(eventId) ||
      !Set.of("PENDING", "ACCEPTED").contains(r.getStatus())
    ) return;
    defaults.evaluate(org, r.getLoanId());
    var data = json.readTree(r.getPayload());
    var c = cases
      .findByIdAndOrganizationId(data.path("defaultReferenceId").asLong(), org)
      .orElse(null);
    String failure = null;
    if (
      c == null ||
      !c.getLoanId().equals(r.getLoanId()) ||
      !"OPEN".equals(c.getStatus()) ||
      !Set.of("RECALL", "MATURITY").contains(c.getObligationType()) ||
      c.getQuantity() <= 0
    ) failure = "The securities obligation is no longer in default";
    if (failure == null && "BUY_IN_REQUESTED".equals(r.getType())) {
      defaults.emit(
        org,
        id,
        1,
        eventId,
        "LIFECYCLE_REQUEST_ACCEPTED",
        Map.of("loanId", r.getLoanId())
      );
      return;
    }
    if (failure == null) {
      long parentId = data.path("buyinRequestId").asLong();
      var parent = requests
        .findByIdAndOrganizationId(parentId, org)
        .orElse(null);
      long quantity = data.path("quantity").asLong();
      BigDecimal price = data.path("price").decimalValue(),
        costs = data.path("costs").decimalValue();
      String reference = data.path("executionReference").asText();
      if (
        parent == null ||
        !"BUY_IN_REQUESTED".equals(parent.getType()) ||
        !"ACCEPTED".equals(parent.getStatus()) ||
        !parent.getLoanId().equals(r.getLoanId()) ||
        json
          .readTree(parent.getPayload())
          .path("defaultReferenceId")
          .asLong() != c.getId()
      ) failure = "Accepted buy-in request does not match the default";
      else if (
        quantity != c.getQuantity() ||
        price.signum() <= 0 ||
        costs.signum() < 0 ||
        reference.isBlank() ||
        reference.length() > 120 ||
        price
          .multiply(BigDecimal.valueOf(quantity))
          .add(costs)
          .compareTo(new BigDecimal("999999999999999999.999999")) > 0
      ) failure =
        "Execution must match the currently outstanding securities quantity and a valid trade reference";
      else {
        var report = new LinkedHashMap<String, Object>();
        report.put("loanId", r.getLoanId());
        report.put("defaultId", c.getId());
        report.put("lifecycleRequestId", parentId);
        report.put("executionRequestId", id);
        report.put("quantity", quantity);
        report.put("price", price);
        report.put("costs", costs);
        report.put("collateralApplied", BigDecimal.ZERO);
        report.put(
          "residualBalance",
          price.multiply(BigDecimal.valueOf(quantity)).add(costs)
        );
        report.put("executionReference", reference);
        report.put("status", "EXECUTED");
        defaults.emit(
          org,
          2000000000000L + id,
          1,
          eventId,
          "BUY_IN_REPORTED",
          report
        );
        return;
      }
    }
    defaults.emit(
      org,
      id,
      1,
      eventId,
      "LIFECYCLE_REQUEST_REJECTED",
      Map.of("reason", failure)
    );
  }
}
