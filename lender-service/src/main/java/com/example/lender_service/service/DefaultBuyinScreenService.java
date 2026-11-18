package com.example.lender_service.service;

import static com.example.lender_service.service.Checks.*;

import com.example.lender_service.model.*;
import com.example.lender_service.repository.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tenant-scoped read model. Reported buy-ins do not imply custody or cash settlement. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DefaultBuyinScreenService {

  private final TenantService tenants;
  private final LoanDefaultRepository defaults;
  private final BuyInRepository buyIns;
  private final BuyinOrderRepository orders;
  private final LoanRepository loans;
  private final BorrowerConnectionRepository borrowers;
  private final SecurityMasterRepository securities;
  private final RecallRepository recalls;
  private final MarginCallRepository margins;
  private final LifecycleRequestRepository requests;
  private final JsonService json;

  public List<Map<String, Object>> snapshot(long org) {
    tenants.exists(org);
    var paging = Pageable.unpaged();
    var allDefaults = defaults.findByOrganizationId(org, paging).getContent();
    var allLoans = loans
      .findByOrganizationId(org, paging)
      .getContent()
      .stream()
      .collect(Collectors.toMap(Loan::getId, Function.identity()));
    var allBorrowers = borrowers
      .findByOrganizationId(org, paging)
      .getContent()
      .stream()
      .collect(
        Collectors.toMap(BorrowerConnection::getId, Function.identity())
      );
    var allSecurities = securities
      .findAllById(
        allLoans.values().stream().map(Loan::getSecurityId).distinct().toList()
      )
      .stream()
      .collect(Collectors.toMap(SecurityMaster::getId, Function.identity()));
    var allBuyIns = buyIns.findByOrganizationId(org, paging).getContent();
    var allOrders=orders.findByOrganizationId(org);
    var allRequests = requests.findByOrganizationId(org, paging).getContent();
    var allRecalls = recalls.findByOrganizationId(org, paging).getContent();
    var allMargins = margins.findByOrganizationId(org, paging).getContent();
    return allDefaults
      .stream()
      .sorted(Comparator.comparing(LoanDefault::getId).reversed())
      .map(def -> {
        var row = new LinkedHashMap<String, Object>();
        var loan = found(Optional.ofNullable(allLoans.get(def.getLoanId())));
        row.put("default", def);
        row.put("orders",allOrders.stream().filter(o->o.getDefaultId().equals(def.getId())).sorted(Comparator.comparing(BuyinOrder::getId).reversed()).toList());
        row.put("reportedData", json.tree(def.getSourceData()));
        row.put("loan", loan);
        row.put(
          "borrowerName",
          Optional.ofNullable(allBorrowers.get(loan.getConnectionId()))
            .map(BorrowerConnection::getName)
            .orElse(null)
        );
        row.put("security", allSecurities.get(loan.getSecurityId()));
        row.put(
          "recall",
          allRecalls
            .stream()
            .filter(
              r ->
                r.getLoanId().equals(loan.getId()) &&
                r.getOwnerReferenceId().equals(def.getObligationId()) &&
                def.getObligationType().contains("RECALL")
            )
            .findFirst()
            .orElse(null)
        );
        row.put(
          "marginCall",
          allMargins
            .stream()
            .filter(
              r ->
                r.getLoanId().equals(loan.getId()) &&
                r.getOwnerReferenceId().equals(def.getObligationId()) &&
                def.getObligationType().contains("MARGIN")
            )
            .findFirst()
            .orElse(null)
        );
        row.put(
          "buyIns",
          allBuyIns
            .stream()
            .filter(
              b ->
                b.getLoanId().equals(loan.getId()) &&
                b.getDefaultReferenceId().equals(def.getOwnerReferenceId())
            )
            .sorted(Comparator.comparing(BuyIn::getId).reversed())
            .map(b -> {
              var item = new LinkedHashMap<String, Object>(
                json.map(json.tree(json.write(b)))
              );
              item.put(
                "executionReference",
                json
                  .tree(b.getSourceData())
                  .path("executionReference")
                  .asText("")
              );
              return item;
            })
            .toList()
        );
        row.put(
          "requests",
          allRequests
            .stream()
            .filter(
              r ->
                r.getLoanId() != null &&
                r.getLoanId().equals(loan.getId()) &&
                Set.of(
                  "BUY_IN_REQUESTED",
                  "BUY_IN_EXECUTION_REQUESTED"
                ).contains(r.getType()) &&
                json
                  .tree(r.getPayload())
                  .path("defaultReferenceId")
                  .asLong(-1) == def.getOwnerReferenceId()
            )
            .sorted(Comparator.comparing(LifecycleRequest::getId).reversed())
            .toList()
        );
        return (Map<String, Object>) row;
      })
      .toList();
  }
}
