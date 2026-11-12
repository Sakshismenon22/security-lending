package com.example.fix_adapter_service;

import com.example.fix_adapter_service.config.*;
import com.example.fix_adapter_service.dto.*;
import com.example.fix_adapter_service.model.*;
import com.example.fix_adapter_service.service.*;
import org.junit.jupiter.api.Test;
import quickfix.*;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MessageCodecTests {
    static final JsonMapper JSON=JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
    static AdapterProperties properties(AdapterSide side) {
        var p=new AdapterProperties();p.setSide(side);p.setInstitutionId(side==AdapterSide.BORROWER?"BORROWER_DEMO":"LENDER_DEMO");p.setSenderCompId(p.getInstitutionId());
        var route=new RouteProperties();route.setCounterpartyId(side==AdapterSide.BORROWER?"LENDER_DEMO":"BORROWER_DEMO");route.setTargetCompId(route.getCounterpartyId());route.setOrganizationIds(Set.of(1L));p.setRoutes(List.of(route));return p;
    }
    static String command(String id) {
        var envelope=new LinkedHashMap<String,Object>();envelope.put("eventId",id);envelope.put("type","LOCATE_REQUESTED");envelope.put("organizationId",1);envelope.put("referenceId",1000);envelope.put("occurredAt",Instant.now().toString());envelope.put("destination","COUNTERPARTY");envelope.put("mslaId",10);envelope.put("lenderExternalId","LENDER_DEMO");envelope.put("data",Map.of("quantity",100,"note","₹ equity\u0001Unicode","price",new java.math.BigDecimal("123.456789")));return JSON.writeValueAsString(envelope);
    }
    static String confirmation(String id) {return JSON.writeValueAsString(Map.of("eventId",id,"type","LOCATE_APPROVED","organizationId",1,"referenceId",1000,"version",1,"occurredAt",Instant.now().toString(),"borrowerExternalId","BORROWER_DEMO","data",Map.of("approvedQuantity",100)));}
    static OutboundMessage outbound(RoutedEnvelope r) {
        var m=new OutboundMessage();m.setEventId(r.envelope().eventId());m.setMessageKind(r.envelope().kind());m.setPayload(r.envelope().payload());m.setPayloadHash(r.envelope().payloadHash());return m;
    }
    static void header(Message message) {message.getHeader().setString(8,"FIX.4.4");message.getHeader().setString(49,"BORROWER_DEMO");message.getHeader().setString(56,"LENDER_DEMO");message.getHeader().setInt(34,1);message.getHeader().setString(52,"20261108-12:00:00.000");}

    @Test void commandRoundTripsWithUnicodeControlCharactersAndExactDecimalPrecision() throws Exception {
        var borrower=properties(AdapterSide.BORROWER);var source=new EnvelopeService(borrower,new RouteService(borrower),JSON).outbound(command("event-1")).orElseThrow();
        var codec=new FixMessageCodec(borrower);var decoded=codec.decode(codec.business(outbound(source)));
        var lender=properties(AdapterSide.LENDER);var target=new EnvelopeService(lender,new RouteService(lender),JSON).inbound(decoded,"FIX.4.4:LENDER_DEMO->BORROWER_DEMO");
        assertEquals(source.envelope().payload(),target.envelope().payload());
        assertEquals(1000,JSON.readTree(target.envelope().payload()).get("referenceId").longValue());
        assertEquals("123.456789",JSON.readTree(target.envelope().payload()).get("data").get("price").asText());
    }
    @Test void commandIncludesDisplayOnlyFixBusinessMessage() {
        var borrower=properties(AdapterSide.BORROWER);var result=new EnvelopeService(borrower,new RouteService(borrower),JSON).outbound(command("fix-view-1")).orElseThrow();
        var view=JSON.readTree(result.envelope().payload()).get("fixMsg");
        assertTrue(view.get("displayOnly").booleanValue());
        assertEquals("AF",view.get("businessFixType").asText());
        assertEquals("U1",view.get("transportFixType").asText());
        assertTrue(view.get("raw").asText().startsWith("8=FIX.4.4|9="));
        assertTrue(view.get("raw").asText().contains("35=AF|"));
        assertTrue(view.get("raw").asText().endsWith("|"));
        assertEquals(100,view.get("values").get("quantity").intValue());
    }
    @Test void confirmationStripsOnlyLocalBorrowerRoutingMetadata() {
        var p=properties(AdapterSide.LENDER);var result=new EnvelopeService(p,new RouteService(p),JSON).outbound(confirmation("confirm-1")).orElseThrow();
        assertFalse(JSON.readTree(result.envelope().payload()).has("borrowerExternalId"));assertEquals(MessageKind.CONFIRMATION,result.envelope().kind());
    }
    @Test void paymentOnlyMessagesAreSkippedAndCannotBeSentOverFix() {
        var p=properties(AdapterSide.BORROWER);var service=new EnvelopeService(p,new RouteService(p),JSON);
        String payment=JSON.writeValueAsString(Map.of("eventId","pay-1","type","FEE_REQUESTED","organizationId",1,"referenceId",1,"occurredAt",Instant.now().toString(),"destination","PAYMENTS","data",Map.of("amount",10)));
        assertTrue(service.outbound(payment).isEmpty());
    }
    @Test void wrongOrganizationCounterpartyDirectionAndUnsupportedTypeAreRejected() throws Exception {
        var p=properties(AdapterSide.BORROWER);var service=new EnvelopeService(p,new RouteService(p),JSON);
        String body=command("invalid-1");
        var node=JSON.readTree(body);var map=new LinkedHashMap<String,Object>(JSON.readValue(body,new tools.jackson.core.type.TypeReference<Map<String,Object>>(){}));
        map.put("organizationId",2);assertThrows(IllegalArgumentException.class,()->service.outbound(JSON.writeValueAsString(map)));
        map.put("organizationId",1);map.put("lenderExternalId","OTHER");assertThrows(IllegalArgumentException.class,()->service.outbound(JSON.writeValueAsString(map)));
        map.put("lenderExternalId","LENDER_DEMO");map.put("type","LOAN_APPROVE");assertThrows(IllegalArgumentException.class,()->service.outbound(JSON.writeValueAsString(map)));
        assertThrows(IllegalArgumentException.class,()->service.inbound(new DecodedFixMessage(MessageKind.COMMAND,"e","a".repeat(64),body),"FIX.4.4:BORROWER_DEMO->LENDER_DEMO"));
    }
    @Test void tamperedAndOversizedPayloadsAreRejected() throws Exception {
        var p=properties(AdapterSide.BORROWER);var r=new EnvelopeService(p,new RouteService(p),JSON).outbound(command("tampered-1")).orElseThrow();var codec=new FixMessageCodec(p);
        var message=codec.business(outbound(r));message.setString(FixMessageCodec.PAYLOAD_HASH,"a".repeat(64));
        assertThrows(IllegalArgumentException.class,()->codec.decode(message));
        p.setMaxPayloadBytes(10);assertThrows(IllegalArgumentException.class,()->codec.decode(codec.business(outbound(r))));
    }
    @Test void customDictionaryValidatesBusinessMessagesAndReceipts() throws Exception {
        var p=properties(AdapterSide.BORROWER);var r=new EnvelopeService(p,new RouteService(p),JSON).outbound(command("dictionary-1")).orElseThrow();var codec=new FixMessageCodec(p);
        var dictionary=new DataDictionary("FIX44-SecuritiesLending.xml");
        for(var message:List.of(codec.business(outbound(r)),codec.receipt(r.envelope().eventId(),r.envelope().payloadHash()))) {
            header(message);var parsed=new Message(message.toString(),dictionary,true);dictionary.validate(parsed);
        }
        assertTrue(dictionary.isRequiredField("U1",FixMessageCodec.PAYLOAD));assertTrue(dictionary.isRequiredField("U3",FixMessageCodec.PAYLOAD_HASH));
    }
}
