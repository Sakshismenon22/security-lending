package com.example.fix_adapter_service;

import com.example.fix_adapter_service.config.AdapterProperties;
import com.example.fix_adapter_service.dto.*;
import com.example.fix_adapter_service.model.*;
import com.example.fix_adapter_service.repository.*;
import com.example.fix_adapter_service.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdapterWorkflowTests {
    @Autowired OutboundQueueService queue;
    @Autowired InboundQueueService inbound;
    @Autowired OutboundDeliveryService delivery;
    @Autowired ReceiptService receipts;
    @Autowired PublicationService publishing;
    @Autowired AdapterOperationsService operations;
    @Autowired OutboundMessageRepository outbound;
    @Autowired InboundMessageRepository received;
    @Autowired KafkaPublicationRepository publications;
    @Autowired EnvelopeService envelopes;
    @Autowired AdapterProperties properties;
    @Autowired MockMvc mvc;
    @MockitoBean(name="fixEngineService") FixEngineService transport;
    @MockitoBean(name="kafkaPublisherService") BusinessEventPublisher publisher;
    static final String SESSION="FIX.4.4:BORROWER_DEMO->LENDER_DEMO";
    @BeforeEach void setup() {properties.setKafkaEnabled(false);when(transport.sessions()).thenReturn(List.of());}
    @AfterEach void restore() {properties.setKafkaEnabled(false);}
    OutboundMessage enqueue(String id) {queue.enqueue(MessageCodecTests.command(id),new SourceRecord("borrower.commands.v1",0,1));return outbound.findByEventId(id).orElseThrow();}
    DecodedFixMessage confirmation(String id) {
        var p=MessageCodecTests.properties(AdapterSide.LENDER);var envelope=new EnvelopeService(p,new RouteService(p),MessageCodecTests.JSON).outbound(MessageCodecTests.confirmation(id)).orElseThrow().envelope();
        return new DecodedFixMessage(MessageKind.CONFIRMATION,envelope.eventId(),envelope.payloadHash(),envelope.payload());
    }
    @Test void kafkaAcceptanceCreatesADurableQueueAndDuplicateIsIdempotent() {
        String id=UUID.randomUUID().toString(),body=MessageCodecTests.command(id);
        queue.enqueue(body,new SourceRecord("borrower.commands.v1",0,1));var retry=queue.enqueue(body,new SourceRecord("borrower.commands.v1",0,2));
        assertTrue(retry.duplicate());assertEquals(1,outbound.count());assertEquals(DeliveryState.QUEUED,outbound.findByEventId(id).orElseThrow().getState());
    }
    @Test void conflictingDuplicateIsRejected() {
        String id=UUID.randomUUID().toString(),body=MessageCodecTests.command(id);queue.enqueue(body,new SourceRecord("borrower.commands.v1",0,1));
        assertThrows(IllegalArgumentException.class,()->queue.enqueue(body.replace("\"quantity\":100","\"quantity\":99"),new SourceRecord("borrower.commands.v1",0,2)));
    }
    @Test void disconnectedSessionsKeepMessagesQueuedWithoutBurningAttempts() {
        var message=enqueue(UUID.randomUUID().toString());when(transport.loggedOn(SESSION)).thenReturn(false);delivery.deliverNext(SESSION);
        assertEquals(DeliveryState.QUEUED,message.getState());assertEquals(0,message.getAttempts());assertNotNull(message.getLastError());
    }
    @Test void successfulSendWaitsForAMatchingDurableReceipt() throws Exception {
        var message=enqueue(UUID.randomUUID().toString());when(transport.loggedOn(SESSION)).thenReturn(true);when(transport.send(eq(SESSION),any())).thenReturn(true);
        delivery.deliverNext(SESSION);assertEquals(DeliveryState.AWAITING_RECEIPT,message.getState());
        receipts.acknowledge(message.getEventId(),message.getPayloadHash(),SESSION);receipts.acknowledge(message.getEventId(),message.getPayloadHash(),SESSION);
        assertEquals(DeliveryState.ACKNOWLEDGED,message.getState());assertNotNull(message.getAcknowledgedAt());
    }
    @Test void forgedReceiptCannotAcknowledgeTheMessage() {
        var message=enqueue(UUID.randomUUID().toString());
        assertThrows(IllegalArgumentException.class,()->receipts.acknowledge(message.getEventId(),"a".repeat(64),SESSION));
        assertThrows(IllegalArgumentException.class,()->receipts.acknowledge(message.getEventId(),message.getPayloadHash(),"FIX.4.4:BORROWER_DEMO->OTHER"));
        assertEquals(DeliveryState.QUEUED,message.getState());
    }
    @Test void unacknowledgedHeadBlocksLaterMessagesForTheSameSession() throws Exception {
        var first=enqueue(UUID.randomUUID().toString());var second=enqueue(UUID.randomUUID().toString());when(transport.loggedOn(SESSION)).thenReturn(true);when(transport.send(eq(SESSION),any())).thenReturn(true);
        delivery.deliverNext(SESSION);delivery.deliverNext(SESSION);assertEquals(1,first.getAttempts());assertEquals(0,second.getAttempts());
        receipts.acknowledge(first.getEventId(),first.getPayloadHash(),SESSION);delivery.deliverNext(SESSION);assertEquals(1,second.getAttempts());
    }
    @Test void peerTimeoutRetriesSameEventAndSuspendsWithoutDroppingIt() throws Exception {
        var message=enqueue(UUID.randomUUID().toString());when(transport.loggedOn(SESSION)).thenReturn(true);when(transport.send(eq(SESSION),any())).thenReturn(true);
        message.setAttempts(properties.getMaxDeliveryAttempts());delivery.deliverNext(SESSION);assertEquals(DeliveryState.SUSPENDED,message.getState());
        operations.retryOutbound(message.getId());delivery.deliverNext(SESSION);assertEquals(DeliveryState.AWAITING_RECEIPT,message.getState());assertEquals(1,message.getAttempts());
    }
    @Test void inboundAndPublicationArePersistedAtomicallyAndDeduplicated() {
        var decoded=confirmation(UUID.randomUUID().toString());inbound.receive(decoded,SESSION);inbound.receive(decoded,SESSION);
        assertEquals(1,received.count());assertEquals(1,publications.count());
        var publication=publications.findAll().get(0);assertEquals("borrower.confirmations.v1",publication.getTopic());assertEquals("1",publication.getPartitionKey());assertEquals(PublicationState.PENDING,publication.getState());
    }
    @Test void wrongSessionOrOrganizationCannotEnterTheInboundQueue() {
        var decoded=confirmation(UUID.randomUUID().toString());assertThrows(IllegalArgumentException.class,()->inbound.receive(decoded,"FIX.4.4:BORROWER_DEMO->OTHER"));assertEquals(0,received.count());assertEquals(0,publications.count());
    }
    @Test void brokerFailureKeepsPublicationPendingAndBlocksLaterEvents() throws Exception {
        inbound.receive(confirmation(UUID.randomUUID().toString()),SESSION);inbound.receive(confirmation(UUID.randomUUID().toString()),SESSION);properties.setKafkaEnabled(true);
        doThrow(new IllegalStateException("broker unavailable")).when(publisher).publish(anyString(),anyString(),anyString(),anyMap());
        publishing.publishNext("1");publishing.publishNext("1");var rows=publications.findAll(org.springframework.data.domain.Sort.by("id"));assertEquals(1,rows.get(0).getAttempts());assertEquals(0,rows.get(1).getAttempts());assertEquals(PublicationState.PENDING,rows.get(0).getState());
        reset(publisher);rows.get(0).setNextAttemptAt(Instant.EPOCH);publishing.publishNext("1");assertEquals(PublicationState.PUBLISHED,rows.get(0).getState());
    }
    @Test void disabledKafkaDoesNotMarkAnUnpublishedMessageAsDelivered() {
        inbound.receive(confirmation(UUID.randomUUID().toString()),SESSION);publishing.publishNext("1");assertEquals(PublicationState.PENDING,publications.findAll().get(0).getState());
    }
    @Test void operationalApiHasNoLendingEndpointsAndUsesStablePagination() throws Exception {
        mvc.perform(get("/api/v1/adapter/status")).andExpect(status().isOk()).andExpect(jsonPath("data.side").value("BORROWER"));
        mvc.perform(get("/api/v1/adapter/outbound?size=10")).andExpect(status().isOk()).andExpect(jsonPath("data.totalElements").value(0));
        mvc.perform(get("/api/v1/adapter/outbound?size=201")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/adapter/loans")).andExpect(status().isNotFound());
    }
}
