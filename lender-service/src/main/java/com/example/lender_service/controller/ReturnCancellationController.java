package com.example.lender_service.controller;

import com.example.lender_service.response.ApiResponse;
import com.example.lender_service.service.ReturnCancellationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ReturnCancellationController {

  private final ReturnCancellationService cancellations;

  @PostMapping("/api/v1/transport/returns/cancel")
  public ApiResponse<?> cancel(
    @RequestBody String body,
    @RequestHeader("x-counterparty-id") String counterparty
  ) {
    return ApiResponse.ok(cancellations.cancel(body, counterparty));
  }
}
