package com.example.borrower_service.controller;

import com.example.borrower_service.dto.AllocationRequest;
import com.example.borrower_service.response.ApiResponse;
import com.example.borrower_service.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/organizations/{org}")
@RequiredArgsConstructor
public class CollateralController {
    private final CollateralService collateral;private final BorrowerReadService read;private final PortfolioService portfolio;
    @GetMapping("/collateral") public ApiResponse<?> list(@PathVariable long org,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) {return ApiResponse.ok(read.list(org,"collateral_position",page,size));}
    @GetMapping("/collateral/history") public ApiResponse<?> history(@PathVariable long org,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) {return ApiResponse.ok(read.list(org,"collateral_history",page,size));}
    @PostMapping("/loans/{id}/collateral") public ApiResponse<?> post(@PathVariable long org,@PathVariable long id,@Valid @RequestBody AllocationRequest r) {return ApiResponse.ok(collateral.post(org,id,r));}
    @PostMapping("/margin-calls/{id}/collateral") public ApiResponse<?> respond(@PathVariable long org,@PathVariable long id,@Valid @RequestBody AllocationRequest r) {return ApiResponse.ok(collateral.respondToMarginCall(org,id,r));}
    @GetMapping("/loans/{id}/valuation") public ApiResponse<?> valuation(@PathVariable long org,@PathVariable long id) {return ApiResponse.ok(portfolio.authoritativeValuation(org,id));}
    @GetMapping("/loans/{id}/collateral-preview") public ApiResponse<?> preview(@PathVariable long org,@PathVariable long id) {return ApiResponse.ok(collateral.preview(org,id));}
    @PostMapping("/collateral/{id}/release") public ApiResponse<?> release(@PathVariable long org,@PathVariable long id) {return ApiResponse.ok(collateral.requestRelease(org,id));}
}
