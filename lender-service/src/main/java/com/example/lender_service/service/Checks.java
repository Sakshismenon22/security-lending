package com.example.lender_service.service;

import java.math.*;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class Checks {
  private Checks() {}

  public static void require(boolean condition, String message) {
    if (!condition) throw new ResponseStatusException(HttpStatus.CONFLICT, message);
  }

  public static <T> T found(Optional<T> value) {
    return value.orElseThrow(
        () ->
            new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Record not found in this organization"));
  }

  public static BigDecimal amount(BigDecimal value) {
    return value.setScale(6, RoundingMode.HALF_UP);
  }
}
