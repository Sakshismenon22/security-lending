package com.example.borrower_service.repository;

import com.example.borrower_service.model.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation,Long> {
    List<Reservation> findByOrganizationId(Long organizationId);
    Page<Reservation> findByOrganizationId(Long organizationId,Pageable pageable);
    Optional<Reservation> findByIdAndOrganizationId(Long id,Long organizationId);
}
