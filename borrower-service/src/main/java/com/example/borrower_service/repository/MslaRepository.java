package com.example.borrower_service.repository;

import com.example.borrower_service.model.Msla;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface MslaRepository extends JpaRepository<Msla,Long> {
    List<Msla> findByOrganizationId(Long organizationId);
    Page<Msla> findByOrganizationId(Long organizationId,Pageable pageable);
    Optional<Msla> findByIdAndOrganizationId(Long id,Long organizationId);
}
