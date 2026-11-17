package com.example.borrower_service.controller;

import com.example.borrower_service.response.ApiResponse;
import com.example.borrower_service.service.DeliveryRecoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/organizations/{org}/delivery-failures")
@RequiredArgsConstructor
public class DeliveryRecoveryController {

  private final DeliveryRecoveryService recovery;

  @GetMapping
  public ApiResponse<?> list(@PathVariable long org) {
    return ApiResponse.ok(recovery.list(org));
  }

  @PostMapping("/{id}/retry")
  public ApiResponse<?> retry(@PathVariable long org, @PathVariable String id) {
    return ApiResponse.ok(recovery.decide(org, id, true));
  }

  @PostMapping("/{id}/close")
  public ApiResponse<?> close(@PathVariable long org, @PathVariable String id) {
    return ApiResponse.ok(recovery.decide(org, id, false));
  }
}
