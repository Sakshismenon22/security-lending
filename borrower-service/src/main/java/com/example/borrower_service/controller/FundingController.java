package com.example.borrower_service.controller;

import com.example.borrower_service.dto.ApproveDepositRequest;
import com.example.borrower_service.dto.FundingRequest;
import com.example.borrower_service.response.ApiResponse;
import com.example.borrower_service.service.BorrowerReadService;
import com.example.borrower_service.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/organizations/{org}")
@RequiredArgsConstructor
public class FundingController {
    private final PaymentService payments;
    private final BorrowerReadService read;
    @GetMapping("/deposits") public ApiResponse<?> listDeposits(@PathVariable long org,@RequestParam(defaultValue = "0") int page,@RequestParam(defaultValue = "50") int size) {return ApiResponse.ok(read.listDeposits(org,page,size));}
    @PostMapping("/deposits") public ApiResponse<?> deposit(@PathVariable long org,@Valid @RequestBody FundingRequest r) {return ApiResponse.ok(payments.funding(org,r,true));}
    @PostMapping("/deposits/{id}/approve") public ApiResponse<?> approveDeposit(@PathVariable long org,@PathVariable long id,@Valid @RequestBody ApproveDepositRequest r) {return ApiResponse.ok(payments.approveDeposit(org,id,r.userId()));}
    @PostMapping("/withdrawals") public ApiResponse<?> withdraw(@PathVariable long org,@Valid @RequestBody FundingRequest r) {return ApiResponse.ok(payments.funding(org,r,false));}
}
