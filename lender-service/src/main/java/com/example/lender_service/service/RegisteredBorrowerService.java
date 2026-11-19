package com.example.lender_service.service;

import static com.example.lender_service.service.Checks.*;

import com.example.lender_service.dto.BorrowerConnectionRequest;
import com.example.lender_service.model.BorrowerConnection;
import com.example.lender_service.repository.BorrowerConnectionRepository;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegisteredBorrowerService {

  private final RegisteredBorrowerClient registry;
  private final BorrowerConnectionRepository connections;
  private final DirectoryService directory;
  private final TenantService tenants;
  private final String institution;

  public RegisteredBorrowerService(
    RegisteredBorrowerClient registry,
    BorrowerConnectionRepository connections,
    DirectoryService directory,
    TenantService tenants,
    @Value(
      "${lendora.borrower-institution-id:BORROWER_DEMO}"
    ) String institution
  ) {
    this.registry = registry;
    this.connections = connections;
    this.directory = directory;
    this.tenants = tenants;
    this.institution = institution;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> available(long org) {
    tenants.exists(org);
    var registered = registry.list();
    var connectedIds = new HashSet<Long>();
    connections
      .findByOrganizationId(org)
      .forEach(c -> connectedIds.add(c.getBorrowerOrganizationId()));
    var available = registered
      .stream()
      .filter(b -> !connectedIds.contains(b.borrowerOrganizationId()))
      .toList();
    return Map.of(
      "content",
      available,
      "totalRegistered",
      registered.size(),
      "alreadyConnected",
      registered.size() - available.size()
    );
  }

  @Transactional
  public BorrowerConnection connect(long org, long directoryId) {
    tenants.exists(org);
    var borrower = registry.find(directoryId);
    tenants.lock(org);
    var existing = connections
      .findByOrganizationId(org)
      .stream()
      .filter(
        c -> c.getBorrowerOrganizationId() == borrower.borrowerOrganizationId()
      )
      .findFirst();
    if (existing.isPresent()) {
      require(
        Boolean.TRUE.equals(existing.get().getActive()),
        "Borrower already has an inactive connection. Review the existing connection instead."
      );
      return existing.get();
    }
    require(
      !institution.isBlank() && institution.length() <= 100,
      "Borrower transport institution is not configured"
    );
    return directory.connect(
      org,
      new BorrowerConnectionRequest(
        institution,
        borrower.borrowerOrganizationId(),
        borrower.name()
      )
    );
  }
}
