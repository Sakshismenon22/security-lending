package com.example.lendora_service.service;

import com.example.lendora_service.dto.AuthContracts.*;
import com.example.lendora_service.model.*;
import com.example.lendora_service.repository.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountService {

  private final AccountOrganizationRepository organizations;
  private final AccountUserRepository users;
  private final AccountSessionRepository sessions;
  private final PasswordHasher passwords;
  private final WorkspaceProvisioner provisioner;
  private final Clock clock;
  private final long sessionHours, rememberDays;
  private final String dummyHash;
  private final SecureRandom random = new SecureRandom();

  public AccountService(
    AccountOrganizationRepository organizations,
    AccountUserRepository users,
    AccountSessionRepository sessions,
    PasswordHasher passwords,
    WorkspaceProvisioner provisioner,
    Clock clock,
    @Value("${lendora.session-hours:12}") long sessionHours,
    @Value("${lendora.remember-days:30}") long rememberDays
  ) {
    this.organizations = organizations;
    this.users = users;
    this.sessions = sessions;
    this.passwords = passwords;
    this.provisioner = provisioner;
    this.clock = clock;
    this.sessionHours = sessionHours;
    this.rememberDays = rememberDays;
    this.dummyHash = passwords.hash(UUID.randomUUID().toString());
  }

  @Transactional
  public Identity register(Registration request) {
    var org = request.organization();
    var admin = request.administrator();
    String email = normalize(admin.email());
    String registration = org
      .registrationNumber()
      .trim()
      .toUpperCase(Locale.ROOT);
    if (
      users.existsByEmail(email) ||
      organizations.existsByTypeAndRegistrationNumber(org.type(), registration)
    ) throw new ResponseStatusException(
      HttpStatus.CONFLICT,
      "An account or organization already exists with these details."
    );
    var organization = new AccountOrganization();
    organization.setName(org.name().trim());
    organization.setType(org.type());
    organization.setRegistrationNumber(registration);
    organization.setCountry(org.country().trim());
    organization.setBaseCurrency(org.baseCurrency());
    organization.setRegisteredAddress(org.registeredAddress().trim());
    organization.setCreatedAt(clock.instant());
    // Stable across a failed HTTP response/transaction, so provisioning can safely be retried.
    organization.setExternalKey(
      "LENDORA_" + digest(org.type() + ":" + registration)
    );
    var user = new AccountUser();
    user.setFullName(admin.fullName().trim());
    user.setEmail(email);
    user.setPasswordHash(passwords.hash(admin.password()));
    user.setActive(true);
    user.setFailedAttempts(0);
    user.setCreatedAt(clock.instant());
    try {
      organizations.saveAndFlush(organization);
      user.setOrganizationId(organization.getId());
      users.saveAndFlush(user);
    } catch (DataIntegrityViolationException e) {
      throw new ResponseStatusException(
        HttpStatus.CONFLICT,
        "An account or organization already exists with these details.",
        e
      );
    }
    long platformId = provisioner.provision(
      org.type(),
      new ProvisionRequest(
        organization.getExternalKey(),
        organization.getId(),
        organization.getName(),
        registration,
        org.baseCurrency(),
        user.getFullName(),
        email
      )
    );
    organization.setPlatformOrganizationId(platformId);
    return identity(user, organization);
  }

  public static class LoginRejected extends ResponseStatusException {

    public LoginRejected() {
      super(HttpStatus.UNAUTHORIZED, "Email or password is incorrect.");
    }
  }

  @Transactional(noRollbackFor = LoginRejected.class)
  public LoginResult login(Login request, String oldToken) {
    var user = users.lockByEmail(normalize(request.email())).orElse(null);
    boolean valid = passwords.verify(
      request.password(),
      user == null ? dummyHash : user.getPasswordHash()
    );
    Instant now = clock.instant();
    if (user == null) throw new LoginRejected();
    if (user.getLockedUntil() != null && !user.getLockedUntil().isAfter(now)) {
      user.setFailedAttempts(0);
      user.setLockedUntil(null);
    }
    if (
      !valid ||
      !user.getActive() ||
      (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now))
    ) {
      if (user.getLockedUntil() == null) {
        user.setFailedAttempts(user.getFailedAttempts() + 1);
        if (user.getFailedAttempts() >= 5) user.setLockedUntil(
          now.plusSeconds(900)
        );
      }
      throw new LoginRejected();
    }
    var org = organizations
      .findById(user.getOrganizationId())
      .orElseThrow(LoginRejected::new);
    if (
      org.getPlatformOrganizationId() == null
    ) throw new ResponseStatusException(
      HttpStatus.SERVICE_UNAVAILABLE,
      "Your workspace is not ready. Contact your organization administrator."
    );
    user.setFailedAttempts(0);
    user.setLockedUntil(null);
    if (validToken(oldToken)) sessions.deleteById(digest(oldToken));
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String token = Base64.getUrlEncoder()
      .withoutPadding()
      .encodeToString(bytes);
    long seconds = Boolean.TRUE.equals(request.rememberMe())
      ? Duration.ofDays(rememberDays).toSeconds()
      : Duration.ofHours(sessionHours).toSeconds();
    var session = new AccountSession();
    session.setTokenHash(digest(token));
    session.setUserId(user.getId());
    session.setCreatedAt(now);
    session.setExpiresAt(now.plusSeconds(seconds));
    sessions.save(session);
    return new LoginResult(
      token,
      identity(user, org),
      Boolean.TRUE.equals(request.rememberMe()) ? seconds : -1
    );
  }

  @Transactional(readOnly = true)
  public Identity me(String token) {
    if (!validToken(token)) throw new ResponseStatusException(
      HttpStatus.UNAUTHORIZED,
      "Please log in to continue."
    );
    var session = sessions
      .findById(digest(token))
      .orElseThrow(() ->
        new ResponseStatusException(
          HttpStatus.UNAUTHORIZED,
          "Please log in to continue."
        )
      );
    if (
      !session.getExpiresAt().isAfter(clock.instant())
    ) throw new ResponseStatusException(
      HttpStatus.UNAUTHORIZED,
      "Your session has expired. Please log in again."
    );
    var user = users
      .findById(session.getUserId())
      .filter(AccountUser::getActive)
      .orElseThrow(() ->
        new ResponseStatusException(
          HttpStatus.UNAUTHORIZED,
          "Please log in to continue."
        )
      );
    return identity(
      user,
      organizations.findById(user.getOrganizationId()).orElseThrow()
    );
  }

  @Transactional
  public void logout(String token) {
    if (validToken(token)) sessions.deleteById(digest(token));
  }

  private Identity identity(AccountUser user, AccountOrganization org) {
    return new Identity(
      new OrganizationView(
        org.getId(),
        org.getName(),
        org.getType(),
        org.getRegistrationNumber(),
        org.getCountry(),
        org.getBaseCurrency(),
        org.getRegisteredAddress(),
        org.getPlatformOrganizationId()
      ),
      new UserView(user.getId(), user.getFullName(), user.getEmail(), "ADMIN"),
      org.getType()
    );
  }

  private String normalize(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private boolean validToken(String token) {
    return token != null && token.matches("[A-Za-z0-9_-]{43}");
  }

  public static String digest(String value) {
    try {
      return HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(
          value.getBytes(StandardCharsets.UTF_8)
        )
      );
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
