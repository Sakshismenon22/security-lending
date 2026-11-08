package com.example.borrower_service.repository;

import com.example.borrower_service.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User,Long> {
    List<User> findByOrganizationId(Long organizationId);
    Page<User> findByOrganizationId(Long organizationId,Pageable pageable);
    Optional<User> findByIdAndOrganizationId(Long id,Long organizationId);
}
