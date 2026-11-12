package com.example.market_data_service.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.market_data_service.model.SecuritiesCurrentPrice;
import com.example.market_data_service.response.Response;
import com.example.market_data_service.service.ISecuritiesCurrentPriceService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/securities-current-price")
@RequiredArgsConstructor
public class SecuritiesCurrentPriceController {

    private final ISecuritiesCurrentPriceService securitiesCurrentPriceService;

    @GetMapping("/get-all-securities-current-prices")
    public ResponseEntity<Response<List<SecuritiesCurrentPrice>>> getAllSecuritiesCurrentPrices() {
        return ResponseEntity.ok(new Response<>(
                200,
                true,
                securitiesCurrentPriceService.getAllSecuritiesCurrentPrices(),
                "Current prices retrieved successfully",
                LocalDateTime.now()
        ));
    }
}
