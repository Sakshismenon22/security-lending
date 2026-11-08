package com.example.borrower_service.controller;

import com.example.borrower_service.response.ApiResponse;
import com.example.borrower_service.service.BorrowerReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/organizations/{org}")
@RequiredArgsConstructor
public class ObligationController {
    private final BorrowerReadService read;
    @GetMapping("/margin-calls") public ApiResponse<?> calls(@PathVariable long org,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) {return ApiResponse.ok(read.list(org,"margin_recall",page,size));}
    @GetMapping("/recalls") public ApiResponse<?> recalls(@PathVariable long org,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) {return ApiResponse.ok(read.list(org,"recall",page,size));}
    @GetMapping("/defaults") public ApiResponse<?> defaults(@PathVariable long org,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) {return ApiResponse.ok(read.list(org,"loan_default",page,size));}
    @GetMapping("/buy-ins") public ApiResponse<?> buyIns(@PathVariable long org,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) {return ApiResponse.ok(read.list(org,"buy_in",page,size));}
}
