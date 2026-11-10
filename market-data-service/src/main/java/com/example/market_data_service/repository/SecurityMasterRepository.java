package com.example.market_data_service.repository;

import com.example.market_data_service.model.SecurityMaster;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SecurityMasterRepository extends JpaRepository<SecurityMaster,Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select security from SecurityMaster security where security.id = :id")
    Optional<SecurityMaster> lockById(Integer id);

    Optional<SecurityMaster>
    findBySymbolIgnoreCaseAndExchangeIgnoreCase(
            String symbol,
            String exchange
    );
}
