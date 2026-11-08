package com.example.lender_service.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "inventory_position")
@Getter
@Setter
@NoArgsConstructor
public class InventoryPosition {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "organization_id", nullable = false)
  private Long organizationId;

  @Column(name = "security_id", nullable = false)
  private Long securityId;

  @Column(name = "total_quantity", nullable = false)
  private Long totalQuantity;

  @Column(name = "reserved_quantity", nullable = false)
  private Long reservedQuantity;

  @Column(name = "loaned_quantity", nullable = false)
  private Long loanedQuantity;

  @Transient
  public long getAvailableQuantity() {
    return totalQuantity - reservedQuantity - loanedQuantity;
  }
}
