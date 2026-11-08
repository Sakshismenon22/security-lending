package com.example.lender_service.controller;

import com.example.lender_service.dto.*;
import com.example.lender_service.repository.*;
import com.example.lender_service.response.ApiResponse;
import com.example.lender_service.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
public class OrganizationController {
  private final DirectoryService directory;
  private final OrganizationRepository organizations;
  private final RoleRepository roles;
  private final ReadService read;

  @PostMapping
  public ApiResponse<?> create(@Valid @RequestBody OrganizationRequest r) {
    return ApiResponse.ok(directory.create(r));
  }

  @GetMapping
  public ApiResponse<?> list() {
    return ApiResponse.ok(organizations.findAll());
  }

  @GetMapping("/{org}")
  public ApiResponse<?> organization(@PathVariable long org) {
    return ApiResponse.ok(Checks.found(organizations.findById(org)));
  }

  @GetMapping("/roles")
  public ApiResponse<?> roles() {
    return ApiResponse.ok(roles.findAll());
  }

  @PostMapping("/{org}/users")
  public ApiResponse<?> member(@PathVariable long org, @Valid @RequestBody MemberRequest r) {
    return ApiResponse.ok(directory.member(org, r));
  }

  @PatchMapping("/{org}/users/{id}")
  public ApiResponse<?> update(
      @PathVariable long org, @PathVariable long id, @Valid @RequestBody MemberUpdateRequest r) {
    return ApiResponse.ok(directory.updateMember(org, id, r));
  }

  @PostMapping("/{org}/borrowers")
  public ApiResponse<?> connect(
      @PathVariable long org, @Valid @RequestBody BorrowerConnectionRequest r) {
    return ApiResponse.ok(directory.connect(org, r));
  }
}
