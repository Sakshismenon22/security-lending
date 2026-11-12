package com.example.market_data_service.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.market_data_service.model.SecuritiesCurrentPrice;
import com.example.market_data_service.repository.SecuritiesCurrentPriceRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SecuritiesCurrentPriceServiceImpl implements ISecuritiesCurrentPriceService {

    private final SecuritiesCurrentPriceRepository repository;

    @Override
    public SecuritiesCurrentPrice addSecuritiesCurrentPrice(SecuritiesCurrentPrice price) {
        return repository.save(price);
    }

    @Override
    public SecuritiesCurrentPrice updateSecuritiesCurrentPrice(SecuritiesCurrentPrice price) {
        if (price.getId() == null || !repository.existsById(price.getId())) {
            throw new IllegalArgumentException("Current price record not found");
        }
        return repository.save(price);
    }

    @Override
    public String deleteSecuritiesCurrentPrice(Integer id) {
        repository.deleteById(id.longValue());
        return "Current price record deleted";
    }

    @Override
    public List<SecuritiesCurrentPrice> getAllSecuritiesCurrentPrices() {
        return repository.findAll();
    }
}
