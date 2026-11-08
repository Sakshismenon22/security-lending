package com.example.borrower_service.controller;

import com.example.borrower_service.repository.SecurityMasterRepository;
import com.example.borrower_service.response.ApiResponse;
import com.example.borrower_service.service.MarketService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/securities")
@RequiredArgsConstructor
public class MarketController {
    private final MarketService market;private final SecurityMasterRepository securities;
    @GetMapping public ApiResponse<?> list(@RequestParam(defaultValue="") String search) {return ApiResponse.ok(securities.findBySymbolContainingIgnoreCaseOrNameContainingIgnoreCase(search,search));}
    @GetMapping("/{id}") public ApiResponse<?> detail(@PathVariable long id) {return ApiResponse.ok(securities.findById(id).orElseThrow(()->new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Security not found")));}
    @GetMapping("/{id}/history") public ApiResponse<?> history(@PathVariable long id) {return ApiResponse.ok(market.history(id));}
    @PostMapping("/import") public ApiResponse<?> importSecurities() {return ApiResponse.ok(market.importSecurities());}
    @PostMapping("/{id}/refresh") public ApiResponse<?> refresh(@PathVariable long id) {return ApiResponse.ok(market.refresh(id));}
}
