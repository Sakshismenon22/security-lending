package com.example.fix_adapter_service.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Table(name="inbound_message")
@Getter @Setter @NoArgsConstructor
public class InboundMessage {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id",nullable=false)
    private Long id;

    @Column(name="event_id",nullable=false,unique=true,length=120)
    private String eventId;

    @Column(name="event_type",nullable=false,length=80)
    private String eventType;

    @Column(name="organization_id",nullable=false)
    private Long organizationId;

    @Column(name="reference_id",nullable=false)
    private Long referenceId;

    @Enumerated(EnumType.STRING)
    @Column(name="message_kind",nullable=false,length=20)
    private MessageKind messageKind;

    @Column(name="session_key",nullable=false,length=240)
    private String sessionKey;

    @Column(name="counterparty_id",nullable=false,length=100)
    private String counterpartyId;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name="payload",nullable=false,columnDefinition="text")
    private String payload;

    @Column(name="payload_hash",nullable=false,length=64)
    private String payloadHash;

    @Column(name="received_at",nullable=false)
    private Instant receivedAt;

}
