package com.example.lender_service.controller;

import com.example.lender_service.dto.*;
import com.example.lender_service.response.ApiResponse;
import com.example.lender_service.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/organizations/{org}")
public class BuyinOrderController {

  private final BuyinOrderService orders;
  private final CollateralLiquidationService liquidation;
  private final BuyinInventoryService receipts;
  private final BuyinLoanCloseoutService closeouts;

  public record SaleRequest(@NotBlank @Size(max=120) String reference,@NotBlank @Size(max=64) String saleScope) {}

  public record ReceiptRequest(@NotBlank @Size(max = 120) String reference) {}

  @GetMapping("/defaults/{id}/collateral-sale-preview")
  public ApiResponse<?> salePreview(@PathVariable long org,@PathVariable long id) {return ApiResponse.ok(liquidation.preview(org,id));}
  @PostMapping("/defaults/{id}/sell-collateral")
  public ApiResponse<?> sellCollateral(@PathVariable long org,@PathVariable long id,@Valid @RequestBody SaleRequest r) {return ApiResponse.ok(liquidation.sell(org,id,r.reference(),r.saleScope()));}

  @GetMapping("/defaults/{id}/loan-closeout-preview")
  public ApiResponse<?> closeoutPreview(@PathVariable long org,@PathVariable long id) {return ApiResponse.ok(closeouts.preview(org,id));}
  @PostMapping("/defaults/{id}/close-loan")
  public ApiResponse<?> closeLoan(@PathVariable long org,@PathVariable long id,@Valid @RequestBody ReceiptRequest r) {return ApiResponse.ok(closeouts.close(org,id,r.reference()));}

  @PostMapping("/defaults/{id}/buy-in-orders")
  public ApiResponse<?> place(
    @PathVariable long org,
    @PathVariable long id,
    @Valid @RequestBody BuyinOrderRequest r
  ) {
    return ApiResponse.ok(orders.place(org, id, r));
  }

  @PostMapping("/buy-in-orders/{id}/match")
  public ApiResponse<?> match(@PathVariable long org, @PathVariable long id) {
    return ApiResponse.ok(orders.match(org, id));
  }

  @PostMapping("/buy-in-orders/{id}/cancel")
  public ApiResponse<?> cancel(@PathVariable long org, @PathVariable long id) {
    return ApiResponse.ok(orders.cancel(org, id));
  }

  @PostMapping("/buy-ins/{id}/inventory-receipts")
  public ApiResponse<?> receive(
    @PathVariable long org,
    @PathVariable long id,
    @Valid @RequestBody ReceiptRequest r
  ) {
    return ApiResponse.ok(receipts.receive(org, id, r.reference()));
  }
}
