package com.example.lifecycle_service.controller;

import com.example.lifecycle_service.service.DefaultScanService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/lifecycle/organizations/{org}/defaults/scan")
public class DefaultScanController {

  private final DefaultScanService scans;
  private final String key;

  public DefaultScanController(
    DefaultScanService scans,
    @Value(
      "${lendora.provisioning-key:${LENDORA_PROVISIONING_KEY:}}"
    ) String key
  ) {
    this.scans = scans;
    this.key = key;
  }

  @PostMapping
  public Map<String, Object> run(
    @PathVariable long org,
    @RequestHeader(
      name = "X-Lendora-Provisioning-Key",
      required = false
    ) String provided
  ) {
    if (key.isBlank()) throw new ResponseStatusException(
      HttpStatus.SERVICE_UNAVAILABLE,
      "Default scan service authentication is not configured"
    );
    if (
      provided == null ||
      !MessageDigest.isEqual(
        key.getBytes(StandardCharsets.UTF_8),
        provided.getBytes(StandardCharsets.UTF_8)
      )
    ) throw new ResponseStatusException(
      HttpStatus.UNAUTHORIZED,
      "Invalid service credentials"
    );
    return Map.of("success", true, "data", scans.run(org));
  }
}
