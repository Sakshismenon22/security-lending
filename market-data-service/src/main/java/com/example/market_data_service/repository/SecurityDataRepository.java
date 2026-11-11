package com.example.market_data_service.repository;

import com.example.market_data_service.model.SecurityData;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SecurityDataRepository
        extends JpaRepository<SecurityData, Long> {

    Optional<SecurityData>
    findBySecurityIdAndTradedDateTimestamp(
            Integer securityId,
            Instant tradedDateTimestamp
    );

    List<SecurityData>
    findBySecurityIdAndTradedDateTimestampBetweenOrderByTradedDateTimestampAsc(
            Integer securityId,
            Instant from,
            Instant to
    );

    List<SecurityData> findBySecurityIdOrderByTradedDateTimestampAsc(Integer securityId);
}
