package com.example.lifecycle_service.repository;

import com.example.lifecycle_service.model.Loan;
import com.example.lifecycle_service.model.Organization;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface LoanRepository extends JpaRepository<Loan, Long> {
  Optional<Loan> findByIdAndOrganizationId(Long id, Long organizationId);

  List<Loan> findAllByStatus(String status);

  @Query("select l.organizationId from Loan l where l.id = :loanId")
  Optional<Long> findOrganizationId(long loanId);

  @Query("select l.id from Loan l, Msla m where l.mslaId = m.id and l.settledAt is not null and m.rebateRate > 0 order by l.id")
  List<Long> findRebateLoanIds();

  @Query("""
      select l.id from Loan l, Msla m
      where l.mslaId = m.id and l.settledAt is not null and m.feeRate > 0 and l.feeFinalized = false
        and (l.closedAt is not null or l.feeProcessedThrough is null or l.feeProcessedThrough < :through)
      order by l.id
      """)
  List<Long> findFeeLoanIds(LocalDate through);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select organization from Organization organization where organization.id = :organizationId")
  Organization lockOrganization(Long organizationId);
}
