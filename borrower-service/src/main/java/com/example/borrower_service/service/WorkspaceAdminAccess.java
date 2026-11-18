package com.example.borrower_service.service;

import com.example.borrower_service.repository.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkspaceAdminAccess {

  private final UserRepository users;
  private final RoleRepository roles;

  public Map<String, Object> snapshot(HttpServletRequest request, long org) {
    Object verified = request.getAttribute("lendora.userEmail");
    if (!(verified instanceof String email) || email.isBlank()) return Map.of(
      "admin",
      false,
      "role",
      "UNVERIFIED"
    );
    var member = users.findByOrganizationIdAndEmailIgnoreCase(org, email);
    if (
      member.isEmpty() || !Boolean.TRUE.equals(member.get().getActive())
    ) return Map.of("admin", false, "role", "INACTIVE_OR_UNREGISTERED");
    var user = member.get();
    String role =
      user.getRoleId() == null
        ? "UNKNOWN"
        : roles
            .findById(user.getRoleId())
            .map(r -> r.getRoleName())
            .orElse("UNKNOWN");
    return Map.of(
      "admin",
      "ADMIN".equals(role),
      "role",
      role,
      "userId",
      user.getId()
    );
  }

  public long requireAdmin(HttpServletRequest request, long org) {
    var access = snapshot(request, org);
    if (
      !Boolean.TRUE.equals(access.get("admin"))
    ) throw new ResponseStatusException(
      HttpStatus.FORBIDDEN,
      "An active workspace administrator is required"
    );
    return ((Number) access.get("userId")).longValue();
  }
}
