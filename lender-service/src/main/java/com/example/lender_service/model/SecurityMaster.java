package com.example.lender_service.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "security_master")
@Getter
@Setter
@NoArgsConstructor
public class SecurityMaster {
  @Id
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "symbol", nullable = false, length = 40)
  private String symbol;

  @Column(name = "exchange", nullable = false, length = 30)
  private String exchange;

  @Column(name = "name", nullable = false, length = 160)
  private String name;

  @Column(name = "isin", length = 20)
  private String isin;

  @Column(name = "currency", nullable = false, length = 3)
  private String currency;

  @Column(name = "price", precision = 24, scale = 6)
  private BigDecimal price;

  @Column(name = "price_at")
  private Instant priceAt;
}
