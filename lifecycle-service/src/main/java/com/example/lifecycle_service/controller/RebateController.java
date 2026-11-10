package com.example.lifecycle_service.controller;

import com.example.lifecycle_service.dto.RebateRunResult;
import com.example.lifecycle_service.service.RebateLifecycleService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/lifecycle/loans/{loanId}/rebates")
@RequiredArgsConstructor
public class RebateController {
  private final RebateLifecycleService rebates;

  @Value("${lifecycle.rebate-demo-enabled:false}")
  private boolean demoEnabled;

  @PostMapping("/run")
  public Map<String, Object> run(@PathVariable long loanId) {
    RebateRunResult result = rebates.process(loanId);
    return Map.of("success", true, "data", result);
  }

  @PostMapping("/demo/accrue-today")
  public Map<String, Object> accrueToday(@PathVariable long loanId) {
    if (!demoEnabled) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Demo accrual is disabled");
    }
    return Map.of("success", true, "data", rebates.accrueToday(loanId));
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ProblemDetail rebateError(ResponseStatusException exception) {
    return exception.getBody();
  }
}
