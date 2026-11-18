package com.example.lender_service.service;

import static com.example.lender_service.service.Checks.*;

import com.example.lender_service.dto.*;
import com.example.lender_service.model.*;
import com.example.lender_service.repository.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Internal demo matching only; a lifecycle-approved fill is required before inventory is booked. */
@Service
@RequiredArgsConstructor
@Transactional
public class BuyinOrderService {

  private final BuyinOrderRepository orders;
  private final LoanDefaultRepository defaults;
  private final LoanRepository loans;
  private final LifecycleRequestRepository requests;
  private final BuyInRepository reports;
  private final LifecycleRequestService lifecycle;
  private final BuyinInventoryService receipts;
  private final TenantService tenants;
  private final MarketService market;
  private final EventService events;
  private final JsonService json;
  private final Clock clock;

  public BuyinOrder place(long org, long defaultId, BuyinOrderRequest r) {
    tenants.lock(org);
    require(Set.of("MARKET", "LIMIT").contains(r.type()), "Unknown order type");
    require(
      "LIMIT".equals(r.type())
        ? r.limitPrice() != null && r.limitPrice().signum() > 0
        : r.limitPrice() == null,
      "Only limit orders require a limit price"
    );
    var previous = orders.findByOrganizationIdAndClientRequestId(
      org,
      r.clientRequestId()
    );
    if (previous.isPresent()) {
      var p = previous.get();
      require(
        p.getDefaultId() == defaultId &&
          p.getBuyinRequestId() == r.buyinRequestId() &&
          p.getQuantity() == r.quantity() &&
          p.getType().equals(r.type()) &&
          equal(p.getLimitPrice(), r.limitPrice()) &&
          equal(p.getCosts(), r.costs()),
        "Client request ID reused for another order"
      );
      return p;
    }
    var def = found(defaults.findByIdAndOrganizationId(defaultId, org));
    var loan = found(loans.findByIdAndOrganizationId(def.getLoanId(), org));
    var parent = found(
      requests.findByIdAndOrganizationId(r.buyinRequestId(), org)
    );
    require(
      "OPEN".equals(def.getStatus()) &&
        Set.of("RECALL", "MATURITY").contains(def.getObligationType()),
      "An open securities default is required"
    );
    require(
      "BUY_IN_REQUESTED".equals(parent.getType()) &&
        "ACCEPTED".equals(parent.getStatus()) &&
        Objects.equals(parent.getLoanId(), loan.getId()) &&
        json.tree(parent.getPayload()).path("defaultReferenceId").asLong() ==
          def.getOwnerReferenceId(),
      "The buy-in request is not accepted for this default"
    );
    require(
      r.quantity() > 0 && r.quantity() <= loan.getOutstandingQuantity(),
      "Order exceeds the outstanding loan quantity"
    );
    long expected = json.tree(def.getSourceData()).path("quantity").asLong();
    require(
      expected <= 0 || expected == r.quantity(),
      "Order must cover the full current default quantity"
    );
    require(
      orders
        .findByOrganizationIdAndDefaultId(org, defaultId)
        .stream()
        .noneMatch(x ->
          Set.of("PENDING", "EXECUTION_PENDING", "FILLED").contains(
            x.getStatus()
          )
        ),
      "A buy-in order already exists for this default"
    );
    require(
      requests
        .findByOrganizationIdAndLoanIdIn(org, List.of(loan.getId()))
        .stream()
        .noneMatch(
          x ->
            "BUY_IN_EXECUTION_REQUESTED".equals(x.getType()) &&
            Set.of("PENDING", "ACCEPTED").contains(x.getStatus()) &&
            json.tree(x.getPayload()).path("buyinRequestId").asLong() ==
              parent.getId()
        ),
      "An execution submission is already pending"
    );
    require(
      reports
        .findByOrganizationId(org, Pageable.unpaged())
        .getContent()
        .stream()
        .noneMatch(
          x ->
            x.getDefaultReferenceId().equals(def.getOwnerReferenceId()) &&
            !Set.of("FAILED", "REJECTED", "CANCELLED").contains(x.getStatus())
        ),
      "This default already has an execution"
    );
    var order = new BuyinOrder();
    order.setOrganizationId(org);
    order.setDefaultId(defaultId);
    order.setLoanId(loan.getId());
    order.setSecurityId(loan.getSecurityId());
    order.setBuyinRequestId(parent.getId());
    order.setClientRequestId(r.clientRequestId());
    order.setType(r.type());
    order.setQuantity(r.quantity());
    order.setLimitPrice(r.limitPrice());
    order.setCosts(r.costs());
    order.setStatus("PENDING");
    order.setExecutionReference("DEMO-BUYIN-" + UUID.randomUUID());
    order.setCreatedAt(clock.instant());
    orders.saveAndFlush(order);
    events.local(
      org,
      "BUY_IN_ORDER_PLACED",
      order.getId(),
      Map.of("order", order, "venue", "INTERNAL_DEMO")
    );
    match(org, order.getId());
    return order;
  }

  private static boolean equal(BigDecimal a, BigDecimal b) {
    return a == null ? b == null : b != null && a.compareTo(b) == 0;
  }

  public BuyinOrder match(long org, long id) {
    tenants.lock(org);
    var order = found(orders.findByIdAndOrganizationId(id, org));
    if ("EXECUTION_PENDING".equals(order.getStatus())) {
      var request = found(
        requests.findByIdAndOrganizationId(order.getExecutionRequestId(), org)
      );
      if (
        Set.of("FAILED", "REJECTED", "CLOSED").contains(request.getStatus())
      ) reject(
        order,
        request.getStatus(),
        Objects.toString(request.getFailureReason(), "Execution not confirmed")
      );
      return order;
    }
    if (!"PENDING".equals(order.getStatus())) return order;
    var def = found(
      defaults.findByIdAndOrganizationId(order.getDefaultId(), org)
    );
    var loan = found(loans.findByIdAndOrganizationId(order.getLoanId(), org));
    var parent = found(
      requests.findByIdAndOrganizationId(order.getBuyinRequestId(), org)
    );
    if (
      !"OPEN".equals(def.getStatus()) ||
      !Set.of("ACTIVE", "DEFAULTED").contains(loan.getStatus()) ||
      loan.getOutstandingQuantity() < order.getQuantity() ||
      !"ACCEPTED".equals(parent.getStatus()) ||
      json
        .tree(def.getSourceData())
        .path("quantity")
        .asLong(order.getQuantity()) != order.getQuantity()
    ) {
      reject(
        order,
        "REJECTED",
        "The default or outstanding quantity has changed"
      );
      return order;
    }
    MarketPrice quote;
    try {
      quote = market.price(order.getSecurityId());
    } catch (org.springframework.web.server.ResponseStatusException e) {
      order.setFailureReason("Waiting for a valid market quote");
      return order;
    }
    if (
      quote.value() == null ||
      quote.value().signum() <= 0 ||
      quote.at() == null ||
      quote.at().isAfter(clock.instant())
    ) {
      order.setFailureReason("Waiting for a valid market quote");
      return order;
    }
    // Internal demo orders use the latest cached price, preserving its actual timestamp.
    order.setFailureReason(null);
    if (
      "LIMIT".equals(order.getType()) &&
      quote.value().compareTo(order.getLimitPrice()) > 0
    ) return order;
    BigDecimal total = quote
      .value()
      .multiply(BigDecimal.valueOf(order.getQuantity()))
      .add(order.getCosts());
    if (total.compareTo(new BigDecimal("999999999999999999.999999")) > 0) {
      reject(order, "REJECTED", "Purchase value is too large");
      return order;
    }
    order.setExecutionPrice(quote.value());
    order.setQuoteAt(quote.at());
    var execution = lifecycle.reportBuyin(
      org,
      order.getDefaultId(),
      new BuyinExecutionRequest(
        "buyin-order-" + order.getId(),
        order.getBuyinRequestId(),
        order.getQuantity(),
        quote.value(),
        order.getCosts(),
        order.getExecutionReference()
      )
    );
    order.setExecutionRequestId(execution.getId());
    order.setStatus("EXECUTION_PENDING");
    events.local(
      org,
      "BUY_IN_ORDER_MATCHED",
      order.getId(),
      Map.of("order", order, "venue", "INTERNAL_DEMO")
    );
    return order;
  }

  public BuyinOrder cancel(long org, long id) {
    tenants.lock(org);
    var order = found(orders.findByIdAndOrganizationId(id, org));
    if ("CANCELLED".equals(order.getStatus())) return order;
    require(
      "PENDING".equals(order.getStatus()),
      "Only unmatched orders can be cancelled"
    );
    order.setStatus("CANCELLED");
    events.local(org, "BUY_IN_ORDER_CANCELLED", id, Map.of("order", order));
    return order;
  }

  public void confirmed(
    BuyIn report,
    long executionRequestId,
    String reference
  ) {
    var order = orders
      .findByOrganizationIdAndExecutionRequestId(
        report.getOrganizationId(),
        executionRequestId
      )
      .orElse(null);
    if (order == null) return;
    require(
      order.getExecutionReference().equals(reference) &&
        report.getQuantity().equals(order.getQuantity()) &&
        equal(report.getPrice(), order.getExecutionPrice()) &&
        equal(report.getCosts(), order.getCosts()),
      "Order execution report does not match the matched trade"
    );
    receipts.receive(report.getOrganizationId(), report.getId(), reference);
    if (!"FILLED".equals(order.getStatus())) {
      order.setStatus("FILLED");
      order.setFailureReason(null);
      order.setFilledAt(clock.instant());
      events.local(
        order.getOrganizationId(),
        "BUY_IN_ORDER_FILLED",
        order.getId(),
        Map.of(
          "order",
          order,
          "buyinId",
          report.getId(),
          "venue",
          "INTERNAL_DEMO"
        )
      );
    }
  }

  public void rejected(long org, long executionRequestId, String reason) {
    orders
      .findByOrganizationIdAndExecutionRequestId(org, executionRequestId)
      .ifPresent(o -> reject(o, "REJECTED", reason));
  }

  private void reject(BuyinOrder order, String status, String reason) {
    if ("FILLED".equals(order.getStatus())) return;
    order.setStatus(status);
    order.setFailureReason(reason);
    events.local(
      order.getOrganizationId(),
      "BUY_IN_ORDER_" + status,
      order.getId(),
      Map.of("order", order)
    );
  }

  public List<BuyinOrder> pending() {
    return orders.findByStatusIn(Set.of("PENDING", "EXECUTION_PENDING"));
  }
}
