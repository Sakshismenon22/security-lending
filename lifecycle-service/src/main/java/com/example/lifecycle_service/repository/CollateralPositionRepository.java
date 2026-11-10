package com.example.lifecycle_service.repository;

import com.example.lifecycle_service.model.CollateralPosition;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollateralPositionRepository extends JpaRepository<CollateralPosition, Long> {
  List<CollateralPosition> findByLoanId(Long loanId);
}
