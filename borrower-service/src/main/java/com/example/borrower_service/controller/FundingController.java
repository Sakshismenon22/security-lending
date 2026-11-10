package com.example.borrower_service.controller;

import com.example.borrower_service.dto.ApproveDepositRequest;
import com.example.borrower_service.dto.FundingRequest;
import com.example.borrower_service.response.ApiResponse;
import com.example.borrower_service.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/organizations/{org}")
@RequiredArgsConstructor
public class FundingController {
    private final PaymentService payments;
    @PostMapping("/deposits") public ApiResponse<?> deposit(@PathVariable long org,@Valid @RequestBody FundingRequest r) {return ApiResponse.ok(payments.funding(org,r,true));}
    @PostMapping("/deposits/{id}/approve") public ApiResponse<?> approveDeposit(@PathVariable long org,@PathVariable long id,@Valid @RequestBody ApproveDepositRequest r) {return ApiResponse.ok(payments.approveDeposit(org,id,r.userId()));}
    @PostMapping("/withdrawals") public ApiResponse<?> withdraw(@PathVariable long org,@Valid @RequestBody FundingRequest r) {return ApiResponse.ok(payments.funding(org,r,false));}
}
