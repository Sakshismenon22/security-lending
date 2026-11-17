package com.example.lendora_service.controller;

import com.example.lendora_service.dto.AuthContracts.*;
import com.example.lendora_service.service.AccountService;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
public class AccountController {

  private final AccountService accounts;
  private final boolean secure;

  public AccountController(
    AccountService accounts,
    @Value("${lendora.secure-cookie:false}") boolean secure
  ) {
    this.accounts = accounts;
    this.secure = secure;
  }

  @PostMapping("/organizations/register")
  public ResponseEntity<?> register(@Valid @RequestBody Registration request) {
    return ResponseEntity.status(201).body(ok(accounts.register(request)));
  }

  @PostMapping("/auth/login")
  public ResponseEntity<?> login(
    @Valid @RequestBody Login request,
    @CookieValue(name = "LENDORA_SESSION", required = false) String oldToken
  ) {
    var result = accounts.login(request, oldToken);
    var cookie = ResponseCookie.from("LENDORA_SESSION", result.token())
      .httpOnly(true)
      .secure(secure)
      .sameSite("Lax")
      .path("/");
    if (result.maxAgeSeconds() > 0) cookie.maxAge(
      Duration.ofSeconds(result.maxAgeSeconds())
    );
    return ResponseEntity.ok()
      .header(HttpHeaders.SET_COOKIE, cookie.build().toString())
      .body(ok(result.identity()));
  }

  @GetMapping("/auth/me")
  public Map<String, Object> me(
    @CookieValue(name = "LENDORA_SESSION", required = false) String token
  ) {
    return ok(accounts.me(token));
  }

  @PostMapping("/auth/logout")
  public ResponseEntity<?> logout(
    @CookieValue(name = "LENDORA_SESSION", required = false) String token
  ) {
    accounts.logout(token);
    return ResponseEntity.ok()
      .header(
        HttpHeaders.SET_COOKIE,
        ResponseCookie.from("LENDORA_SESSION", "")
          .httpOnly(true)
          .secure(secure)
          .sameSite("Lax")
          .path("/")
          .maxAge(Duration.ZERO)
          .build()
          .toString()
      )
      .body(ok(Map.of("loggedOut", true)));
  }

  @PostMapping("/auth/password-reset")
  public void reset() {
    throw new ResponseStatusException(
      HttpStatus.SERVICE_UNAVAILABLE,
      "Password recovery is not configured. Contact your organization administrator."
    );
  }

  private Map<String, Object> ok(Object data) {
    return Map.of("success", true, "data", data);
  }
}
