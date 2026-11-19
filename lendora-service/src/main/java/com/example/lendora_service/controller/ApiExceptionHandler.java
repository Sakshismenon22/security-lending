package com.example.lendora_service.controller;

import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<?> status(ResponseStatusException e) {
    return ResponseEntity.status(e.getStatusCode()).body(
      Map.of(
        "success",
        false,
        "message",
        e.getReason() == null ? "Request failed" : e.getReason()
      )
    );
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<?> invalid(MethodArgumentNotValidException e) {
    return ResponseEntity.badRequest().body(
      Map.of(
        "success",
        false,
        "message",
        e
          .getBindingResult()
          .getFieldErrors()
          .stream()
          .map(x -> x.getField() + ": " + x.getDefaultMessage())
          .findFirst()
          .orElse("Invalid request")
      )
    );
  }

  @ExceptionHandler(
    org.springframework.http.converter.HttpMessageNotReadableException.class
  )
  public ResponseEntity<?> unreadable() {
    return ResponseEntity.badRequest().body(
      Map.of("success", false, "message", "Invalid request body.")
    );
  }
}
