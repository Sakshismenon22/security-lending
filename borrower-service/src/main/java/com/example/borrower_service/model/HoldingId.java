package com.example.borrower_service.model;

import lombok.*;
import java.io.Serializable;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class HoldingId implements Serializable {
    private Long organizationId;
    private Long securityId;
}
