package com.example.lender_service.controller;

import com.example.lender_service.repository.SecurityMasterRepository;
import com.example.lender_service.response.ApiResponse;
import com.example.lender_service.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/securities")
@RequiredArgsConstructor
public class MarketController {
  private final MarketService market;
  private final SecurityMasterRepository securities;

  @GetMapping
  public ApiResponse<?> list() {
    return ApiResponse.ok(securities.findAll());
  }

  @GetMapping("/{id}")
  public ApiResponse<?> detail(@PathVariable long id) {
    return ApiResponse.ok(Checks.found(securities.findById(id)));
  }

  @GetMapping("/{id}/history")
  public ApiResponse<?> history(@PathVariable long id) {
    return ApiResponse.ok(market.history(id));
  }

  @PostMapping("/import")
  public ApiResponse<?> importSecurities() {
    return ApiResponse.ok(market.importSecurities());
  }

  @PostMapping("/{id}/refresh")
  public ApiResponse<?> refresh(@PathVariable long id) {
    return ApiResponse.ok(market.refresh(id));
  }
}
