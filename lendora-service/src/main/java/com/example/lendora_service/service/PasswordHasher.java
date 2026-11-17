package com.example.lendora_service.service;

import java.security.*;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.stereotype.Component;

@Component
public class PasswordHasher {

  private static final int ITERATIONS = 600000;
  private final SecureRandom random = new SecureRandom();

  public String hash(String password) {
    byte[] salt = new byte[16];
    random.nextBytes(salt);
    return (
      "pbkdf2_sha256$" +
      ITERATIONS +
      "$" +
      Base64.getEncoder().encodeToString(salt) +
      "$" +
      Base64.getEncoder().encodeToString(derive(password, salt, ITERATIONS))
    );
  }

  public boolean verify(String password, String encoded) {
    try {
      var parts = encoded.split("\\$");
      if (parts.length != 4 || !"pbkdf2_sha256".equals(parts[0])) return false;
      int iterations = Integer.parseInt(parts[1]);
      if (iterations < 100000 || iterations > 2000000) return false;
      return MessageDigest.isEqual(
        Base64.getDecoder().decode(parts[3]),
        derive(password, Base64.getDecoder().decode(parts[2]), iterations)
      );
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  private byte[] derive(String password, byte[] salt, int iterations) {
    var spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
    try {
      return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        .generateSecret(spec)
        .getEncoded();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Password hashing unavailable", e);
    } finally {
      spec.clearPassword();
    }
  }
}
