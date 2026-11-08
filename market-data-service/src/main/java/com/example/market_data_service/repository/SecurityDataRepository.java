package com.example.market_data_service.repository;

import com.example.market_data_service.model.SecurityData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
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

    @Modifying
    @Query(value = """
        INSERT INTO security_data (
            security_id,
            traded_date_timestamp,
            open,
            close,
            high,
            low,
            volume
        )
        VALUES (
            :securityId,
            :timestamp,
            :open,
            :close,
            :high,
            :low,
            :volume
        )
        ON CONFLICT (
            security_id,
            traded_date_timestamp
        )
        DO UPDATE SET
            open = EXCLUDED.open,
            close = EXCLUDED.close,
            high = EXCLUDED.high,
            low = EXCLUDED.low,
            volume = EXCLUDED.volume
        """, nativeQuery = true)
    void upsert(
            @Param("securityId") Integer securityId,
            @Param("timestamp") Instant timestamp,
            @Param("open") BigDecimal open,
            @Param("close") BigDecimal close,
            @Param("high") BigDecimal high,
            @Param("low") BigDecimal low,
            @Param("volume") Long volume
    );

    @Query("""
    SELECT data
    FROM SecurityData data
    WHERE data.security.id = :securityId
    ORDER BY data.tradedDateTimestamp ASC
    """)
    List<SecurityData> findAllHistory(
            @Param("securityId") Integer securityId
    );
}
