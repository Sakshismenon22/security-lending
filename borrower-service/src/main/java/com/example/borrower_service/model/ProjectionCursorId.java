package com.example.borrower_service.model;
import lombok.*;
import java.io.Serializable;
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class ProjectionCursorId implements Serializable {
    private String resource;
    private Long referenceId;
}
