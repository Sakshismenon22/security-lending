package com.example.lender_service.service;

import static com.example.lender_service.service.Checks.*;

import com.example.lender_service.dto.*;
import com.example.lender_service.model.*;
import com.example.lender_service.repository.*;
import java.time.Clock;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class InventoryService {
  private final InventoryPositionRepository inventory;
  private final InventoryHistoryRepository history;
  private final SecurityMasterRepository securities;
  private final TenantService tenants;
  private final EventService events;
  private final Clock clock;

  public InventoryPosition create(long org, InventoryRequest r) {
    tenants.lock(org);
    found(securities.findById(r.securityId()));
    require(
        inventory.findByOrganizationIdAndSecurityId(org, r.securityId()).isEmpty(),
        "Inventory already exists for this security");
    var p = new InventoryPosition();
    p.setOrganizationId(org);
    p.setSecurityId(r.securityId());
    p.setTotalQuantity(r.quantity());
    p.setReservedQuantity(0L);
    p.setLoanedQuantity(0L);
    inventory.saveAndFlush(p);
    movement(p, "INVENTORY_ADDED", r.quantity(), r.reference());
    return p;
  }

  public InventoryPosition adjust(long org, long id, InventoryAdjustmentRequest r) {
    tenants.lock(org);
    var p = found(inventory.findByIdAndOrganizationId(id, org));
    long total = Math.addExact(p.getTotalQuantity(), r.quantityDelta());
    require(
        total >= p.getReservedQuantity() + p.getLoanedQuantity(),
        "Inventory cannot fall below reserved and loaned quantity");
    p.setTotalQuantity(total);
    movement(p, "INVENTORY_ADJUSTED", r.quantityDelta(), r.reference());
    return p;
  }

  public InventoryPosition position(long org, long security) {
    return found(inventory.findByOrganizationIdAndSecurityId(org, security));
  }

  public long available(InventoryPosition p) {
    return p.getTotalQuantity() - p.getReservedQuantity() - p.getLoanedQuantity();
  }

  public void movement(InventoryPosition p, String type, long quantity, String reference) {
    var h = new InventoryHistory();
    h.setOrganizationId(p.getOrganizationId());
    h.setInventoryId(p.getId());
    h.setType(type);
    h.setQuantity(quantity);
    h.setReference(reference);
    h.setCreatedAt(clock.instant());
    history.save(h);
    events.local(
        p.getOrganizationId(),
        type,
        p.getId(),
        Map.of(
            "securityId",
            p.getSecurityId(),
            "quantity",
            quantity,
            "totalQuantity",
            p.getTotalQuantity(),
            "reservedQuantity",
            p.getReservedQuantity(),
            "loanedQuantity",
            p.getLoanedQuantity()));
  }
}
