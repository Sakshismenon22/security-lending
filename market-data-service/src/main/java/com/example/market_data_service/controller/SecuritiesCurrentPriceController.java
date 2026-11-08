package com.example.market_data_service.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.market_data_service.service.ISecuritiesCurrentPriceService;

import lombok.RequiredArgsConstructor;

@RestController 
@RequestMapping ("/api/v1/securities-current-price")
@RequiredArgsConstructor 
public class SecuritiesCurrentPriceController {

    private ISecuritiesCurrentPriceService securitiesCurrentPriceService;
    
}
