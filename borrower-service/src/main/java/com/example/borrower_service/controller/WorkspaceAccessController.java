package com.example.borrower_service.controller;

import com.example.borrower_service.response.ApiResponse;
import com.example.borrower_service.service.WorkspaceAdminAccess;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class WorkspaceAccessController {

  private final WorkspaceAdminAccess access;

  @GetMapping("/api/v1/organizations/{org}/admin-access")
  public ApiResponse<?> access(
    @PathVariable long org,
    HttpServletRequest request
  ) {
    return ApiResponse.ok(access.snapshot(request, org));
  }
}
