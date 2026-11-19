package com.example.lender_service.controller;

import com.example.lender_service.response.ApiResponse;
import com.example.lender_service.service.RegisteredBorrowerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/organizations/{org}/borrower-directory")
@RequiredArgsConstructor
public class RegisteredBorrowerController {

  private final RegisteredBorrowerService borrowers;

  public record ConnectBorrower(@Positive long directoryId) {}

  @GetMapping
  public ApiResponse<?> available(@PathVariable long org) {
    return ApiResponse.ok(borrowers.available(org));
  }

  @PostMapping("/connections")
  public ApiResponse<?> connect(
    @PathVariable long org,
    @Valid @RequestBody ConnectBorrower request
  ) {
    return ApiResponse.ok(borrowers.connect(org, request.directoryId()));
  }
}
