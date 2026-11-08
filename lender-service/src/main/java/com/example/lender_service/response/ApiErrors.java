package com.example.lender_service.response;

import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiErrors {
  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<ProblemDetail> state(ResponseStatusException e) {
    return ResponseEntity.status(e.getStatusCode())
        .body(
            ProblemDetail.forStatusAndDetail(
                e.getStatusCode(), e.getReason() == null ? "Request failed" : e.getReason()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ProblemDetail> invalid(IllegalArgumentException e) {
    return ResponseEntity.badRequest()
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage()));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ProblemDetail> integrity(DataIntegrityViolationException e) {
    return ResponseEntity.status(409)
        .body(
            ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "Duplicate record, invalid reference, or quantity/balance constraint violation"));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException e) {
    var p = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
    p.setProperty(
        "errors",
        e.getBindingResult().getFieldErrors().stream()
            .map(
                f ->
                    Map.of("field", f.getField(), "message", String.valueOf(f.getDefaultMessage())))
            .toList());
    return ResponseEntity.badRequest().body(p);
  }
}
