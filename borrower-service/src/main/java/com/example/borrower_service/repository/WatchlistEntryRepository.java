package com.example.borrower_service.repository;

import com.example.borrower_service.model.WatchlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface WatchlistEntryRepository extends JpaRepository<WatchlistEntry,Long> {
    List<WatchlistEntry> findByOrganizationId(Long organizationId);
    Page<WatchlistEntry> findByOrganizationId(Long organizationId,Pageable pageable);
    Optional<WatchlistEntry> findByIdAndOrganizationId(Long id,Long organizationId);
}
