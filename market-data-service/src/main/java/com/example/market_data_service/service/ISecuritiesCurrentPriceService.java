package com.example.market_data_service.service;

import java.util.List;

import com.example.market_data_service.model.SecuritiesCurrentPrice;


public interface ISecuritiesCurrentPriceService {
    
    SecuritiesCurrentPrice addSecuritiesCurrentPrice(SecuritiesCurrentPrice securitiesCurrentPrice);

    SecuritiesCurrentPrice updateSecuritiesCurrentPrice(SecuritiesCurrentPrice securitiesCurrentPrice);

    String deleteSecuritiesCurrentPrice(Integer id);

    List<SecuritiesCurrentPrice> getAllSecuritiesCurrentPrices();
}
