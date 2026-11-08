package com.example.lender_service.controller;

import com.example.lender_service.dto.*;
import com.example.lender_service.response.ApiResponse;
import com.example.lender_service.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/organizations/{org}/inventory")
@RequiredArgsConstructor
public class InventoryController {
  private final InventoryService inventory;

  @PostMapping
  public ApiResponse<?> create(@PathVariable long org, @Valid @RequestBody InventoryRequest r) {
    return ApiResponse.ok(inventory.create(org, r));
  }

  @PostMapping("/{id}/adjustments")
  public ApiResponse<?> adjust(
      @PathVariable long org,
      @PathVariable long id,
      @Valid @RequestBody InventoryAdjustmentRequest r) {
    return ApiResponse.ok(inventory.adjust(org, id, r));
  }
}
