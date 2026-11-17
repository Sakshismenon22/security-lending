package com.example.fix_adapter_service.controller;

import com.example.fix_adapter_service.response.ApiResponse;
import com.example.fix_adapter_service.service.AdapterOperationsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** Transport operations only. There are no lending, approval, or calculation endpoints. */
@RestController
@RequestMapping("/api/v1/adapter")
@RequiredArgsConstructor
public class AdapterController {

  private final AdapterOperationsService operations;

  @GetMapping("/status")
  public ApiResponse<?> status() {
    return ApiResponse.ok(operations.status());
  }

  @GetMapping("/outbound")
  public ApiResponse<?> outbound(
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "50") int size
  ) {
    return ApiResponse.ok(operations.outbound(page, size));
  }

  @GetMapping("/inbound")
  public ApiResponse<?> inbound(
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "50") int size
  ) {
    return ApiResponse.ok(operations.inbound(page, size));
  }

  @GetMapping("/publications")
  public ApiResponse<?> publications(
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "50") int size
  ) {
    return ApiResponse.ok(operations.publications(page, size));
  }

  @PostMapping("/outbound/{id}/retry")
  public ApiResponse<?> retryOutbound(@PathVariable long id) {
    return ApiResponse.ok(operations.retryOutbound(id));
  }

  @PostMapping("/publications/{id}/retry")
  public ApiResponse<?> retryPublication(@PathVariable long id) {
    return ApiResponse.ok(operations.retryPublication(id));
  }

  @GetMapping("/failures")
  public ApiResponse<?> failures(
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "50") int size
  ) {
    return ApiResponse.ok(operations.failures(page, size));
  }

  @PostMapping("/outbound/{id}/close")
  public ApiResponse<?> closeOutbound(@PathVariable long id) {
    return ApiResponse.ok(operations.closeOutbound(id));
  }

  @PostMapping("/publications/{id}/close")
  public ApiResponse<?> closePublication(@PathVariable long id) {
    return ApiResponse.ok(operations.closePublication(id));
  }

  @GetMapping("/outbound/{id}")
  public ApiResponse<?> delivery(@PathVariable long id) {
    return ApiResponse.ok(operations.delivery(id));
  }

  @GetMapping("/publications/{id}")
  public ApiResponse<?> publication(@PathVariable long id) {
    return ApiResponse.ok(operations.publication(id));
  }
}
