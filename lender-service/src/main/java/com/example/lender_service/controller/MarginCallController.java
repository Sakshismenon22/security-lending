package com.example.lender_service.controller;

import com.example.lender_service.dto.*;
import com.example.lender_service.response.ApiResponse;
import com.example.lender_service.service.MarginCallService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/organizations/{org}")
@RequiredArgsConstructor
public class MarginCallController {
  private final MarginCallService marginCalls;

  @PostMapping("/margin-calls/{id}/confirm")
  public ApiResponse<?> confirm(
      @PathVariable long org,
      @PathVariable long id,
      @Valid @RequestBody SettlementRequest request) {
    return ApiResponse.ok(marginCalls.confirm(org, id, request));
  }
}
