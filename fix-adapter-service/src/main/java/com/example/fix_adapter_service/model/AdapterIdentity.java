package com.example.fix_adapter_service.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Table(name="adapter_identity")
@Getter @Setter @NoArgsConstructor
public class AdapterIdentity {
    @Id
    @Column(name="id",nullable=false)
    private Integer id;

    @Enumerated(EnumType.STRING)
    @Column(name="side",nullable=false,length=20)
    private AdapterSide side;

    @Column(name="institution_id",nullable=false,length=100)
    private String institutionId;

    @Column(name="sender_comp_id",nullable=false,length=64)
    private String senderCompId;

    @Column(name="created_at",nullable=false)
    private Instant createdAt;

}
