package com.example.lifecycle_service.repository;

import com.example.lifecycle_service.model.FeeSecurity;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface FeeSecurityRepository extends Repository<FeeSecurity, Long> {
  Optional<FeeSecurity> findById(long id);
}
