package com.example.lender_service.service;

import static com.example.lender_service.service.Checks.*;

import com.example.lender_service.model.*;
import com.example.lender_service.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TenantService {
  private final OrganizationRepository organizations;
  private final BorrowerConnectionRepository connections;

  public Organization lock(long id) {
    return found(organizations.lockById(id));
  }

  public void exists(long id) {
    found(organizations.findById(id));
  }

  public BorrowerConnection connection(long org, long id) {
    var c = found(connections.findByIdAndOrganizationId(id, org));
    require(c.getActive(), "Borrower connection is inactive");
    return c;
  }
}
