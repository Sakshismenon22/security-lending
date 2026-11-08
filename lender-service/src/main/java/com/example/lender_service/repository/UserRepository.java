package com.example.lender_service.repository;

import com.example.lender_service.model.User;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface UserRepository extends JpaRepository<User, Long> {
  Page<User> findByOrganizationId(Long organizationId, Pageable pageable);

  Optional<User> findByIdAndOrganizationId(Long id, Long organizationId);

  long countByOrganizationId(Long organizationId);

  long countByOrganizationIdAndRoleIdAndActiveTrue(Long organizationId, Long roleId);
}
