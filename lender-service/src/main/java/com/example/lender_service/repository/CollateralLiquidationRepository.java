package com.example.lender_service.repository;

import com.example.lender_service.model.CollateralLiquidation;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollateralLiquidationRepository
  extends JpaRepository<CollateralLiquidation, Long>
{
  Optional<CollateralLiquidation> findByOrganizationIdAndClientRequestId(
    Long org,
    String clientId
  );
  List<CollateralLiquidation> findByOrganizationIdAndLoanId(
    Long org,
    Long loan
  );
}
