package com.example.lender_service.controller;

import com.example.lender_service.dto.*;
import com.example.lender_service.response.ApiResponse;
import com.example.lender_service.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/organizations/{org}")
@RequiredArgsConstructor
public class RequestDecisionController {
  private final ReservationService reservations;

  @PostMapping("/locates/{id}/approve")
  public ApiResponse<?> locate(
      @PathVariable long org, @PathVariable long id, @Valid @RequestBody DecisionRequest r) {
    return ApiResponse.ok(reservations.decideLocate(org, id, r.quantity(), true, r.reason()));
  }

  @PostMapping("/locates/{id}/reject")
  public ApiResponse<?> rejectLocate(
      @PathVariable long org, @PathVariable long id, @Valid @RequestBody RejectionRequest r) {
    return ApiResponse.ok(reservations.decideLocate(org, id, 0, false, r.reason()));
  }

  @PostMapping("/reservations/{id}/approve")
  public ApiResponse<?> reserve(
      @PathVariable long org, @PathVariable long id, @Valid @RequestBody DecisionRequest r) {
    return ApiResponse.ok(reservations.decide(org, id, r.quantity(), true, r.reason()));
  }

  @PostMapping("/reservations/{id}/reject")
  public ApiResponse<?> rejectReservation(
      @PathVariable long org, @PathVariable long id, @Valid @RequestBody RejectionRequest r) {
    return ApiResponse.ok(reservations.decide(org, id, 0, false, r.reason()));
  }
}
