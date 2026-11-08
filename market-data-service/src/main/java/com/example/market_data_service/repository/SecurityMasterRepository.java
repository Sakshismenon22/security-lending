package com.example.market_data_service.repository;

import com.example.market_data_service.model.SecurityMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SecurityMasterRepository extends JpaRepository<SecurityMaster,Integer> {
    Optional<SecurityMaster>
    findBySymbolIgnoreCaseAndExchangeIgnoreCase(
            String symbol,
            String exchange
    );
}
