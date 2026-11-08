package com.example.lender_service.model;

import java.time.Instant;

/** Common metadata only; each externally owned domain view has a separate entity/table. */
public interface LifecycleProjection {
  Long getId();

  Long getOrganizationId();

  void setOrganizationId(Long id);

  Long getConnectionId();

  void setConnectionId(Long id);

  Long getLoanId();

  void setLoanId(Long id);

  Long getOwnerReferenceId();

  void setOwnerReferenceId(Long id);

  Long getSourceVersion();

  void setSourceVersion(Long version);

  String getSourceData();

  void setSourceData(String data);

  String getStatus();

  void setStatus(String status);

  void setCreatedAt(Instant at);
}
