package com.example.fix_adapter_service.service;

import com.example.fix_adapter_service.config.AdapterProperties;
import com.example.fix_adapter_service.dto.*;
import com.example.fix_adapter_service.model.*;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;

/** Validates transport envelopes and routing. Domain approval/calculation is deliberately absent. */
@Service
public class EnvelopeService {
    private final AdapterProperties properties;private final RouteService routes;private final JsonMapper json;private final FixMessageViewFactory fixViews;
    private static final Set<String> COMMANDS=Set.of("AGREEMENT_PROPOSED","LOCATE_REQUESTED","RESERVATION_REQUESTED","RESERVATION_RELEASE_REQUESTED","LOAN_REQUESTED","COLLATERAL_PLEDGE_REQUESTED","COLLATERAL_RELEASE_REQUESTED","RETURN_REQUESTED","FEE_PAYMENT_RECEIVED");
    private static final Set<String> PAYMENTS=Set.of("DEPOSIT_REQUESTED","WITHDRAWAL_REQUESTED","FEE_REQUESTED");
    private static final Set<String> CONFIRMATIONS=Set.of("LENDER_REFERENCE_UPDATED","AGREEMENT_ACCEPTED","AGREEMENT_REJECTED","LOCATE_APPROVED","LOCATE_REJECTED","LOCATE_EXPIRED","RESERVATION_CONFIRMED","RESERVATION_REJECTED","RESERVATION_RELEASED","RESERVATION_EXPIRED","RESERVATION_UPDATED","LOAN_SETTLED","LOAN_REJECTED","RETURN_SETTLED","RETURN_REJECTED","COLLATERAL_PLEDGED","COLLATERAL_REJECTED","COLLATERAL_RELEASED","COLLATERAL_RELEASE_REJECTED","PAYMENT_CONFIRMED","PAYMENT_REJECTED","FEE_BILL_ISSUED","ACCRUAL_REPORTED","REBATE_ACCRUED","REBATE_PAID","VALUATION_REPORTED","MARGIN_CALL_UPDATED","RECALL_UPDATED","DEFAULT_REPORTED","BUY_IN_REPORTED","LOAN_STATUS_UPDATED","HOLDING_IMPORTED");
    public EnvelopeService(AdapterProperties properties,RouteService routes,JsonMapper json) {this.properties=properties;this.routes=routes;this.json=json;this.fixViews=new FixMessageViewFactory();}
    public Optional<RoutedEnvelope> outbound(String body) {
        JsonNode root=parse(body);
        if(properties.getSide()==AdapterSide.BORROWER && "PAYMENTS".equals(optionalText(root,"destination"))) {
            if(!PAYMENTS.contains(text(root,"type")))throw invalid("Unsupported payment-only command");
            common(root);return Optional.empty();
        }
        MessageKind kind=properties.getSide()==AdapterSide.BORROWER?MessageKind.COMMAND:MessageKind.CONFIRMATION;
        var validated=validate(root,kind);
        String counterparty=text(root,kind==MessageKind.COMMAND?"lenderExternalId":"borrowerExternalId");
        var route=routes.counterparty(counterparty,validated.organizationId());
        validated=withFixMessage(validated,route.getTargetCompId());
        return Optional.of(new RoutedEnvelope(validated,routes.sessionKey(route),counterparty));
    }
    public RoutedEnvelope inbound(DecodedFixMessage message,String sessionKey) {
        MessageKind expected=properties.getSide()==AdapterSide.BORROWER?MessageKind.CONFIRMATION:MessageKind.COMMAND;
        if(message.kind()!=expected)throw invalid("Wrong business-message direction for this adapter side");
        var root=parse(message.payload());var envelope=validate(root,expected);
        if(!envelope.eventId().equals(message.eventId()) || !envelope.payloadHash().equals(message.payloadHash()))throw invalid("FIX envelope ID/hash does not match the JSON envelope");
        var route=routes.session(sessionKey,envelope.organizationId());
        if(expected==MessageKind.COMMAND && !properties.getInstitutionId().equals(text(root,"lenderExternalId")))throw invalid("Command is addressed to another institution");
        return new RoutedEnvelope(envelope,sessionKey,route.getCounterpartyId());
    }
    private JsonNode parse(String body) {
        if(body==null || body.getBytes(StandardCharsets.UTF_8).length>properties.getMaxPayloadBytes())throw invalid("Envelope exceeds maximum UTF-8 payload size");
        JsonNode root=json.readTree(body);if(root==null || !root.isObject())throw invalid("JSON envelope must be an object");return root;
    }
    private void common(JsonNode root) {
        if(!text(root,"eventId").matches("[A-Za-z0-9_.:-]{1,120}"))throw invalid("Invalid eventId");
        if(!text(root,"type").matches("[A-Z][A-Z0-9_]{0,79}"))throw invalid("Invalid event type");
        positive(root,"organizationId");positive(root,"referenceId");Instant.parse(text(root,"occurredAt"));
        if(root.get("data")==null || !root.get("data").isObject())throw invalid("Envelope data must be an object");
    }
    private ValidatedEnvelope validate(JsonNode root,MessageKind kind) {
        common(root);String type=text(root,"type");
        if(kind==MessageKind.COMMAND) {
            if(!COMMANDS.contains(type))throw invalid("Unsupported counterparty command: "+type);
            if(!"COUNTERPARTY".equals(text(root,"destination")))throw invalid("Command destination must be COUNTERPARTY");
            positive(root,"mslaId");text(root,"lenderExternalId");
        } else {
            if(!CONFIRMATIONS.contains(type))throw invalid("Unsupported borrower confirmation: "+type);
            positive(root,"version");
        }
        @SuppressWarnings("unchecked") var normalized=(Map<String,Object>)canonical(root);
        // This is local adapter routing metadata, not part of the borrower's confirmation DTO.
        normalized.remove("borrowerExternalId");
        String payload=json.writeValueAsString(normalized);
        if(payload.getBytes(StandardCharsets.UTF_8).length>properties.getMaxPayloadBytes())throw invalid("Normalized envelope exceeds maximum size");
        return new ValidatedEnvelope(text(root,"eventId"),type,positive(root,"organizationId"),positive(root,"referenceId"),kind,payload,hash(payload));
    }
    @SuppressWarnings("unchecked")
    private ValidatedEnvelope withFixMessage(ValidatedEnvelope envelope,String targetCompId) {
        var payload=(Map<String,Object>)json.readValue(envelope.payload(),new tools.jackson.core.type.TypeReference<LinkedHashMap<String,Object>>(){});
        payload.put("fixMsg",fixViews.create(envelope.eventType(),envelope.eventId(),payload.get("occurredAt"),payload.get("data"),envelope.kind(),properties.getSenderCompId(),targetCompId));
        var normalized=(Map<String,Object>)canonical(json.readTree(json.writeValueAsString(payload)));
        String body=json.writeValueAsString(normalized);
        return new ValidatedEnvelope(envelope.eventId(),envelope.eventType(),envelope.organizationId(),envelope.referenceId(),envelope.kind(),body,hash(body));
    }
    private Object canonical(JsonNode node) {
        if(node.isObject()) {var map=new TreeMap<String,Object>();for(var field:node.properties())map.put(field.getKey(),canonical(field.getValue()));return map;}
        if(node.isArray()) {var list=new ArrayList<>();for(var value:node)list.add(canonical(value));return list;}
        if(node.isIntegralNumber())return node.bigIntegerValue();
        if(node.isNumber())return node.decimalValue().stripTrailingZeros();
        if(node.isBoolean())return node.booleanValue();
        if(node.isNull())return null;
        return node.asText();
    }
    private static String optionalText(JsonNode node,String field) {var value=node.get(field);return value!=null && value.isTextual()?value.asText():null;}
    private static String text(JsonNode node,String field) {String value=optionalText(node,field);if(value==null || value.isBlank())throw invalid("Missing or invalid "+field);return value;}
    private static long positive(JsonNode node,String field) {
        var value=node.get(field);
        if(value==null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue()<=0)throw invalid("Expected positive integer "+field);
        return value.longValue();
    }
    public static String hash(String body) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private static IllegalArgumentException invalid(String message) {return new IllegalArgumentException(message);}
}
