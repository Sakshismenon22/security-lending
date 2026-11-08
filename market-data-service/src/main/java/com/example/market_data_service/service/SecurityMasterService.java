package com.example.market_data_service.service;


import com.example.market_data_service.model.SecurityMaster;
import com.example.market_data_service.response.Response;

import java.util.List;

public interface SecurityMasterService {

    public SecurityMaster addSecurity(SecurityMaster securityMaster);

    public List<SecurityMaster> getAllSecurities();

}
