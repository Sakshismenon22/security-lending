package com.example.lender_service.controller;

import com.example.lender_service.response.ApiResponse;
import com.example.lender_service.service.DefaultBuyinScreenService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/organizations/{org}/lenddesk/defaults-buyins")
@RequiredArgsConstructor
public class DefaultBuyinScreenController {

  private final DefaultBuyinScreenService screen;
  private final com.example.lender_service.service.DefaultScanService scans;

  @PostMapping("/scan")
  public ApiResponse<?> scan(@PathVariable long org) {return ApiResponse.ok(scans.run(org));}

  @GetMapping
  public ApiResponse<?> snapshot(@PathVariable long org) {
    return ApiResponse.ok(screen.snapshot(org));
  }
}
