package com.example.fix_adapter_service.repository;

import com.example.fix_adapter_service.model.AdapterIdentity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;

public interface AdapterIdentityRepository extends JpaRepository<AdapterIdentity,Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from AdapterIdentity i where i.id=1")
    Optional<AdapterIdentity> lockIdentity();
}
