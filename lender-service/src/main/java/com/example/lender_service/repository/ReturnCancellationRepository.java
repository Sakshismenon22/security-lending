package com.example.lender_service.repository;

import com.example.lender_service.model.ReturnCancellation;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReturnCancellationRepository
  extends JpaRepository<ReturnCancellation, String>
{
  Optional<ReturnCancellation> findByConnectionIdAndBorrowerReturnId(
    Long connectionId,
    Long borrowerReturnId
  );
}
