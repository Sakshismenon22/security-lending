package com.example.borrower_service.controller;

import com.example.borrower_service.response.ApiResponse;
import com.example.borrower_service.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/organizations/{org}")
@RequiredArgsConstructor
public class PortfolioController {
    private final PortfolioService portfolio;private final BorrowerReadService read;
    @GetMapping("/dashboard") public ApiResponse<?> dashboard(@PathVariable long org) {return ApiResponse.ok(portfolio.dashboard(org));}
    @GetMapping("/account") public ApiResponse<?> account(@PathVariable long org) {return ApiResponse.ok(portfolio.account(org));}
    @GetMapping("/transactions") public ApiResponse<?> transactions(@PathVariable long org,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) {return ApiResponse.ok(read.list(org,"trader_account_transaction",page,size));}
    @GetMapping("/holdings") public ApiResponse<?> holdings(@PathVariable long org) {return ApiResponse.ok(portfolio.holdings(org));}
    @GetMapping("/positions") public ApiResponse<?> positions(@PathVariable long org) {return ApiResponse.ok(portfolio.positions(org));}
    @GetMapping("/watchlist") public ApiResponse<?> watchlist(@PathVariable long org) {return ApiResponse.ok(portfolio.watchlist(org));}
    @PutMapping("/watchlist/{security}") public ApiResponse<?> addWatch(@PathVariable long org,@PathVariable long security) {return ApiResponse.ok(portfolio.watch(org,security,true));}
    @DeleteMapping("/watchlist/{security}") public ApiResponse<?> removeWatch(@PathVariable long org,@PathVariable long security) {return ApiResponse.ok(portfolio.watch(org,security,false));}
    @GetMapping("/notifications") public ApiResponse<?> notifications(@PathVariable long org,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) {return ApiResponse.ok(read.list(org,"notification",page,size));}
    @PostMapping("/notifications/{id}/read") public ApiResponse<?> markRead(@PathVariable long org,@PathVariable long id) {portfolio.markRead(org,id);return ApiResponse.ok("Marked as read");}
}
