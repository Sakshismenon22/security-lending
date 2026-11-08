package com.example.lender_service.repository;

import com.example.lender_service.model.ConfirmationVersion;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface ConfirmationVersionRepository extends JpaRepository<ConfirmationVersion, Long> {
  Optional<ConfirmationVersion> findByConnectionIdAndResourceAndReferenceId(
      Long connectionId, String resource, Long referenceId);
}
