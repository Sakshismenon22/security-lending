package com.example.borrower_service.controller;

import org.springframework.http.HttpStatus;
import com.example.borrower_service.dto.*;
import com.example.borrower_service.repository.*;
import com.example.borrower_service.response.ApiResponse;
import com.example.borrower_service.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
public class OrganizationController {
    private final DirectoryService directory;private final OrganizationRepository organizations;private final RoleRepository roles;private final BorrowerReadService read;
    @PostMapping public ApiResponse<?> create(@Valid @RequestBody OrganizationRequest r) {return ApiResponse.ok(directory.organization(r));}
    @GetMapping public ApiResponse<?> list() {return ApiResponse.ok(organizations.findAll());}
    @GetMapping("/{org}") public ApiResponse<?> detail(@PathVariable long org) {return ApiResponse.ok(organizations.findById(org).orElseThrow(()->new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND,"Organization not found")));}
    @GetMapping("/roles") public ApiResponse<?> roles() {return ApiResponse.ok(roles.findAll());}
    @GetMapping("/{org}/users") public ApiResponse<?> users(@PathVariable long org,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) {return ApiResponse.ok(read.list(org,"users",page,size));}
    @PostMapping("/{org}/users") public ApiResponse<?> add(@PathVariable long org,@Valid @RequestBody MemberRequest r) {return ApiResponse.ok(directory.member(org,r));}
    @PatchMapping("/{org}/users/{id}") public ApiResponse<?> update(@PathVariable long org,@PathVariable long id,@Valid @RequestBody MemberUpdateRequest r) {return ApiResponse.ok(directory.updateMember(org,id,r));}
    @GetMapping("/{org}/audit") public ApiResponse<?> audit(@PathVariable long org,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) {return ApiResponse.ok(read.list(org,"audit_event",page,size));}
}
