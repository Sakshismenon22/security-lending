package com.example.lifecycle_service.controller;

import com.example.lifecycle_service.dto.MarginCallRequest;
import com.example.lifecycle_service.model.MarginCoverageDecision;
import com.example.lifecycle_service.service.MarginCoverageService;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/lifecycle")
@RequiredArgsConstructor
public class MarginCoverageController {
  private final MarginCoverageService marginCoverageService;

  @GetMapping("/loans/{loanId}/coverage")
  public ResponseEntity<Map<String, Object>> getCoverage(@PathVariable Long loanId) {
    MarginCoverageDecision decision = marginCoverageService.evaluateLoan(loanId);
    return ResponseEntity.ok(Map.of("success", true, "data", decision, "timestamp", Instant.now()));
  }

  @PostMapping("/loans/{loanId}/margin-calls")
  public ResponseEntity<Map<String, Object>> issueMarginCall(
      @PathVariable Long loanId, @RequestBody(required = false) MarginCallRequest request) {
    var amount = request != null ? request.amount() : null;
    MarginCoverageDecision decision = marginCoverageService.triggerMarginCallForLoan(loanId, amount);
    return ResponseEntity.ok(Map.of("success", true, "data", decision, "timestamp", Instant.now()));
  }
}
