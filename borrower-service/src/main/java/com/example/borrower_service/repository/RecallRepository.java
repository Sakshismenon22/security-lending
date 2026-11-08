package com.example.borrower_service.repository;

import com.example.borrower_service.model.Recall;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface RecallRepository extends JpaRepository<Recall,Long> {
    List<Recall> findByOrganizationId(Long organizationId);
    Page<Recall> findByOrganizationId(Long organizationId,Pageable pageable);
    Optional<Recall> findByIdAndOrganizationId(Long id,Long organizationId);
}
