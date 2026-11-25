package com.example.market_data_service.service;

import com.example.market_data_service.config.MarketDataCacheConfig;
import com.example.market_data_service.config.MarketDataCacheConfig.Invalidate;
import com.example.market_data_service.model.SecurityMaster;
import com.example.market_data_service.repository.SecurityMasterRepository;
import lombok.AllArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class SecurityMasterServiceImpl implements SecurityMasterService{

    private final SecurityMasterRepository securityMasterRepository;
    private final ApplicationEventPublisher events;

    @Override
    public SecurityMaster addSecurity(SecurityMaster securityMaster) {
        var stored = securityMasterRepository.save(securityMaster);
        events.publishEvent(new Invalidate(MarketDataCacheConfig.SECURITIES, MarketDataCacheConfig.ALL_SECURITIES));
        return stored;
    }

    @Override
    @Cacheable(cacheNames = MarketDataCacheConfig.SECURITIES, key = "'" + MarketDataCacheConfig.ALL_SECURITIES + "'")
    public List<SecurityMaster> getAllSecurities() {
        return securityMasterRepository.findAll();
    }
}
