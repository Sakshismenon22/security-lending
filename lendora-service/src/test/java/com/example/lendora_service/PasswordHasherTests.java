package com.example.lendora_service;

import static org.junit.jupiter.api.Assertions.*;

import com.example.lendora_service.service.PasswordHasher;
import org.junit.jupiter.api.Test;

class PasswordHasherTests {

  @Test
  void hashesAreSaltedAndDoNotChangePasswordWhitespace() {
    var hasher = new PasswordHasher();
    String hash = hasher.hash(" passphrase-2026 ");
    assertNotEquals(hash, hasher.hash(" passphrase-2026 "));
    assertTrue(hasher.verify(" passphrase-2026 ", hash));
    assertFalse(hasher.verify("passphrase-2026", hash));
    assertFalse(hasher.verify("anything", "invalid"));
  }
}
