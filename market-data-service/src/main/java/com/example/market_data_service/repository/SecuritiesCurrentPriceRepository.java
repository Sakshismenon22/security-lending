package com.example.market_data_service.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.market_data_service.model.SecuritiesCurrentPrice;

@Repository
public interface SecuritiesCurrentPriceRepository
        extends JpaRepository<SecuritiesCurrentPrice, Long> {

    Optional<SecuritiesCurrentPrice>
    findBySecurityId(Integer securityId);
}
