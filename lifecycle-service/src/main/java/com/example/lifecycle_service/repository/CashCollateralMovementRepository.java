package com.example.lifecycle_service.repository;

import com.example.lifecycle_service.dto.CashCollateralChange;
import com.example.lifecycle_service.model.CashCollateralMovement;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface CashCollateralMovementRepository extends Repository<CashCollateralMovement, Long> {
  @Query("""
      select new com.example.lifecycle_service.dto.CashCollateralChange(h.createdAt, h.type, p.cashAmount)
      from CashCollateralMovement h, CollateralPosition p
      where h.positionId = p.id and p.loanId = :loanId and p.assetType = 'CASH'
        and h.type in ('PLEDGE_CONFIRMED', 'RELEASE_CONFIRMED') and h.createdAt < :before
      order by h.createdAt, h.id
      """)
  List<CashCollateralChange> cashChangesBefore(long loanId, Instant before);
}
