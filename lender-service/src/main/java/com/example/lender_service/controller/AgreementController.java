package com.example.lender_service.controller;

import com.example.lender_service.dto.AgreementOfferRequest;
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

  @PostMapping
  public ApiResponse<?> offer(
      @PathVariable long org, @Valid @RequestBody AgreementOfferRequest request) {
    return ApiResponse.ok(agreements.offer(org, request));
  }

  @GetMapping("/{id}")
  public ApiResponse<?> detail(@PathVariable long org, @PathVariable long id) {
    return ApiResponse.ok(read.agreement(org, id));
  }

}
