package com.example.lender_service.controller;

import com.example.lender_service.response.ApiResponse;
import com.example.lender_service.service.ReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/organizations/{org}")
@RequiredArgsConstructor
public class ReadController {
  private final ReadService read;

  @GetMapping(
      "/{resource:users|borrowers|inventory|inventory-history|agreements|locates|reservations|loans|collateral|collateral-history|returns|lifecycle-requests|valuations|margin-calls|recalls|fees|rebates|accruals|defaults|buy-ins|rebate-payments|ledger|notifications|audit}")
  public ApiResponse<?> list(
      @PathVariable long org,
      @PathVariable String resource,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    return ApiResponse.ok(read.list(org, resource, page, size));
  }

  @GetMapping(
      "/{resource:valuations|margin-calls|recalls|fees|rebates|accruals|defaults|buy-ins}/{id}")
  public ApiResponse<?> projection(
      @PathVariable long org, @PathVariable String resource, @PathVariable long id) {
    return ApiResponse.ok(read.projection(org, resource, id));
  }

  @GetMapping("/dashboard")
  public ApiResponse<?> dashboard(@PathVariable long org) {
    return ApiResponse.ok(read.dashboard(org));
  }

  @GetMapping("/account")
  public ApiResponse<?> account(@PathVariable long org) {
    return ApiResponse.ok(read.account(org));
  }

  @PostMapping("/notifications/{id}/read")
  public ApiResponse<?> markRead(@PathVariable long org, @PathVariable long id) {
    read.markRead(org, id);
    return ApiResponse.ok("Marked as read");
  }
}
