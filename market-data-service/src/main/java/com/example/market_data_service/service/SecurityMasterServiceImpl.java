package com.example.market_data_service.service;

import com.example.market_data_service.model.SecurityMaster;
import com.example.market_data_service.repository.SecurityMasterRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class SecurityMasterServiceImpl implements SecurityMasterService{

    private final SecurityMasterRepository securityMasterRepository;

    @Override
    public SecurityMaster addSecurity(SecurityMaster securityMaster) {
        return securityMasterRepository.save(securityMaster);
    }

    @Override
    public List<SecurityMaster> getAllSecurities() {
        return securityMasterRepository.findAll();
    }
}
