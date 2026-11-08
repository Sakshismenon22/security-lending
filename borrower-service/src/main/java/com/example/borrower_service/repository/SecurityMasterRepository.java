package com.example.borrower_service.repository;

import com.example.borrower_service.model.SecurityMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface SecurityMasterRepository extends JpaRepository<SecurityMaster,Long> {
    List<SecurityMaster> findBySymbolContainingIgnoreCaseOrNameContainingIgnoreCase(String symbol,String name);
}
