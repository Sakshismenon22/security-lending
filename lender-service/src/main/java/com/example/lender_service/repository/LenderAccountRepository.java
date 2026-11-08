package com.example.lender_service.repository;

import com.example.lender_service.model.LenderAccount;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface LenderAccountRepository extends JpaRepository<LenderAccount, Long> {
  Page<LenderAccount> findByOrganizationId(Long organizationId, Pageable pageable);

  Optional<LenderAccount> findByIdAndOrganizationId(Long id, Long organizationId);

  Optional<LenderAccount> findByOrganizationId(Long organizationId);
}
