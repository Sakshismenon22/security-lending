package com.example.borrower_service.controller;

import com.example.borrower_service.response.ApiResponse;
import com.example.borrower_service.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;

@RestController
@RequestMapping("/api/v1/organizations/{org}")
@RequiredArgsConstructor
public class BillingController {
    private final PaymentService payments;private final BorrowerReadService read;private final PortfolioService portfolio;
    @GetMapping("/loans/{loanId}/{resource:rebate-accruals|rebate-settlements|rebate-payments}")
    public ApiResponse<?> loanRebates(@PathVariable long org, @PathVariable long loanId,
            @PathVariable String resource, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(read.loanRebates(org, loanId, resource, page, size));
    }
    @GetMapping("/fees") public ApiResponse<?> fees(@PathVariable long org,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) {return ApiResponse.ok(read.list(org,"lending_fee",page,size));}
    @PostMapping("/fees/{id}/payments") public ApiResponse<?> pay(@PathVariable long org,@PathVariable long id,@RequestHeader("Idempotency-Key") String key) {return ApiResponse.ok(payments.payFee(org,id,key));}
    @GetMapping("/rebates") public ApiResponse<?> rebates(@PathVariable long org,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) {return ApiResponse.ok(read.list(org,"rebate",page,size));}
    @GetMapping("/rebate-payments") public ApiResponse<?> rebatePayments(@PathVariable long org,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) {return ApiResponse.ok(read.list(org,"rebate_payment",page,size));}
    @GetMapping("/accruals") public ApiResponse<?> accruals(@PathVariable long org,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) {return ApiResponse.ok(read.list(org,"fee_accrual_view",page,size));}
    @GetMapping("/payments") public ApiResponse<?> payments(@PathVariable long org,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) {return ApiResponse.ok(read.list(org,"payment_request",page,size));}
    @GetMapping(value="/statement.csv",produces="text/csv") public ResponseEntity<String> statement(@PathVariable long org) {return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=borrower-statement.csv").body(portfolio.statement(org));}
}
