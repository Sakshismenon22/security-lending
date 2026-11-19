package com.example.lifecycle_service.repository;

import com.example.lifecycle_service.model.DefaultCaseRecord;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DefaultCaseRecordRepository
  extends JpaRepository<DefaultCaseRecord, Long>
{
  List<DefaultCaseRecord> findByOrganizationIdAndLoanId(Long org, Long loan);
  Optional<DefaultCaseRecord> findByIdAndOrganizationId(Long id, Long org);
  List<DefaultCaseRecord> findByOrganizationIdAndStatus(Long org,String status);
  long countByOrganizationIdAndStatus(Long org,String status);
  List<DefaultCaseRecord> findByStatus(String status);
}
