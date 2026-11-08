package com.example.borrower_service.controller;

import com.example.borrower_service.dto.OrderRequest;
import com.example.borrower_service.response.ApiResponse;
import com.example.borrower_service.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/organizations/{org}")
@RequiredArgsConstructor
public class TradingController {
    private final TradingService trading;private final BorrowerReadService read;
    @PostMapping("/orders") public ApiResponse<?> order(@PathVariable long org,@Valid @RequestBody OrderRequest r) {return ApiResponse.ok(trading.order(org,r));}
    @GetMapping("/orders") public ApiResponse<?> orders(@PathVariable long org,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) {return ApiResponse.ok(read.list(org,"trade_order",page,size));}
    @PostMapping("/orders/{id}/cancel") public ApiResponse<?> cancel(@PathVariable long org,@PathVariable long id) {return ApiResponse.ok(trading.cancel(org,id));}
    @GetMapping("/trades") public ApiResponse<?> trades(@PathVariable long org,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) {return ApiResponse.ok(read.list(org,"trade_history",page,size));}
}
