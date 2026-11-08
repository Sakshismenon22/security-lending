package com.example.lender_service.controller;

import com.example.lender_service.dto.RejectionRequest;
import com.example.lender_service.response.ApiResponse;
import com.example.lender_service.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/organizations/{org}/agreements")
@RequiredArgsConstructor
public class AgreementController {
  private final AgreementService agreements;
  private final ReadService read;

  @GetMapping("/{id}")
  public ApiResponse<?> detail(@PathVariable long org, @PathVariable long id) {
    return ApiResponse.ok(read.agreement(org, id));
  }

  @PostMapping("/{id}/approve")
  public ApiResponse<?> approve(@PathVariable long org, @PathVariable long id) {
    return ApiResponse.ok(agreements.decide(org, id, true, "Approved"));
  }

  @PostMapping("/{id}/reject")
  public ApiResponse<?> reject(
      @PathVariable long org, @PathVariable long id, @Valid @RequestBody RejectionRequest r) {
    return ApiResponse.ok(agreements.decide(org, id, false, r.reason()));
  }
}
