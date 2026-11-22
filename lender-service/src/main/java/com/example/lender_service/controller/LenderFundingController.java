package com.example.lender_service.controller;

import com.example.lender_service.response.ApiResponse;
import com.example.lender_service.service.LenderFundingService;
import com.example.lender_service.service.WorkspaceAdminAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/organizations/{org}/lenddesk/funding")
@RequiredArgsConstructor
public class LenderFundingController {
  private final LenderFundingService funding;
  private final WorkspaceAdminAccess admins;

  record DepositInput(
      @NotBlank @Size(max = 120) String clientRequestId,
      @NotNull @Positive @Digits(integer = 18, fraction = 6) BigDecimal amount
  ) {}

  @GetMapping
  public ApiResponse<?> balance(@PathVariable long org) {
    return ApiResponse.ok(funding.balances(org));
  }

  @PostMapping("/deposits")
  public ApiResponse<?> request(@PathVariable long org, @Valid @RequestBody DepositInput input) {
    return ApiResponse.ok(funding.request(org, input.clientRequestId(), input.amount()));
  }

  @GetMapping("/deposits")
  public ApiResponse<?> deposits(@PathVariable long org,
      @RequestParam(defaultValue = "0") @Min(0) int page, HttpServletRequest request) {
    admins.requireAdmin(request, org);
    return ApiResponse.ok(funding.list(org, page));
  }

  @PostMapping("/deposits/{id}/approve")
  public ApiResponse<?> approve(@PathVariable long org, @PathVariable long id,
      HttpServletRequest request) {
    return ApiResponse.ok(funding.approve(org, id, admins.requireAdmin(request, org)));
  }
}
