package com.example.fix_adapter_service.service;

import com.example.fix_adapter_service.model.MessageKind;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Produces a human-readable FIX 4.4 business-message view for audit and UI use.
 *
 * <p>The adapter continues to transmit its bilateral U1/U2 envelopes. This view is deliberately
 * separate from that transport: it shows the equivalent securities-lending business message using
 * pipe delimiters, while the real wire message continues to use SOH delimiters and tags 9001-9003.
 */
final class FixMessageViewFactory {
    private static final DateTimeFormatter FIX_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HH:mm:ss.SSS").withZone(ZoneOffset.UTC);

    Map<String, Object> create(
            String eventType,
            String eventId,
            Object occurredAt,
            Object dataValue,
            MessageKind transportKind,
            String senderCompId,
            String targetCompId) {
        var data = dataValue instanceof Map<?, ?> map ? map : Map.of();
        var mapping = mapping(eventType, transportKind);
        var fields = new ArrayList<FixField>();
        fields.add(new FixField("35", "MsgType", mapping.businessFixType()));
        fields.add(new FixField("49", "SenderCompID", senderCompId));
        fields.add(new FixField("56", "TargetCompID", targetCompId));
        fields.add(new FixField("34", "MsgSeqNum", "0"));
        fields.add(new FixField("52", "SendingTime", fixTime(occurredAt)));
        fields.add(new FixField("11", "ClOrdID", eventId));
        addBusinessFields(fields, eventType, data, occurredAt);
        fields.add(new FixField("58", "Text", mapping.description()));
        fields.add(new FixField("9004", "BusinessEventType", eventType));

        String raw = wire(fields);
        var tags = new ArrayList<Map<String, String>>();
        tags.add(tag("8", "BeginString", "FIX.4.4"));
        tags.add(tag("9", "BodyLength", bodyLength(fields)));
        for (var field : fields) tags.add(tag(field.tag(), field.name(), field.value()));
        tags.add(tag("10", "CheckSum", checksum(fields)));

        var view = new LinkedHashMap<String, Object>();
        view.put("format", "FIX.4.4");
        view.put("displayDelimiter", "|");
        view.put("displayOnly", true);
        view.put("messageName", mapping.messageName());
        view.put("businessFixType", mapping.businessFixType());
        view.put("transportFixType", transportKind.fixType());
        view.put("interactionSequence", mapping.sequence());
        view.put("businessDescription", mapping.description());
        view.put("raw", raw);
        view.put("tags", tags);
        view.put("values", data);
        return view;
    }

    private void addBusinessFields(
            List<FixField> fields, String eventType, Map<?, ?> data, Object occurredAt) {
        value(data, "symbol", "securitySymbol", "securityId", "collateralSecurityId")
                .ifPresent(value -> fields.add(new FixField("55", "Symbol", value)));
        value(data, "quantity", "approvedQuantity", "releasedQuantity", "returnedQuantity")
                .ifPresent(value -> fields.add(new FixField("38", "OrderQty", value)));
        value(data, "feeRate", "lendingFeeRate", "rebateRate", "amount")
                .ifPresent(value -> fields.add(new FixField("44", "Price", value)));
        if (eventType.contains("STATUS") || eventType.contains("RECALL") || eventType.contains("MARGIN"))
            fields.add(new FixField("326", "SecurityStatus", eventType));
        fields.add(new FixField("60", "TransactTime", fixTime(occurredAt)));
    }

    private Optional<String> value(Map<?, ?> values, String... keys) {
        for (String key : keys) {
            Object value = values.get(key);
            if (value != null) return Optional.of(sanitize(String.valueOf(value)));
        }
        return Optional.empty();
    }

    private String wire(List<FixField> fields) {
        String body = body(fields);
        String prefix = "8=FIX.4.4\u00019=" + body.getBytes(StandardCharsets.UTF_8).length + "\u0001";
        int sum = 0;
        for (byte value : (prefix + body).getBytes(StandardCharsets.UTF_8)) sum = (sum + (value & 0xff)) % 256;
        return (prefix + body + "10=" + String.format("%03d", sum) + "\u0001").replace('\u0001', '|');
    }

    private String bodyLength(List<FixField> fields) {
        return String.valueOf(body(fields).getBytes(StandardCharsets.UTF_8).length);
    }

    private String checksum(List<FixField> fields) {
        String body = body(fields);
        String prefix = "8=FIX.4.4\u00019=" + body.getBytes(StandardCharsets.UTF_8).length + "\u0001";
        int sum = 0;
        for (byte value : (prefix + body).getBytes(StandardCharsets.UTF_8)) sum = (sum + (value & 0xff)) % 256;
        return String.format("%03d", sum);
    }

    private String body(List<FixField> fields) {
        var body = new StringBuilder();
        for (var field : fields)
            body.append(field.tag()).append('=').append(sanitize(field.value())).append('\u0001');
        return body.toString();
    }

    private String fixTime(Object value) {
        try {
            return FIX_TIME.format(Instant.parse(String.valueOf(value)));
        } catch (Exception ignored) {
            return FIX_TIME.format(Instant.now());
        }
    }

    private String sanitize(String value) {
        return value.replace('\u0001', ' ').replace('|', '/');
    }

    private Map<String, String> tag(String tag, String name, String value) {
        return Map.of("tag", tag, "name", name, "value", value);
    }

    private Mapping mapping(String type, MessageKind kind) {
        return switch (type) {
            case "LOCATE_REQUESTED" -> new Mapping(1, "Request For Security", "AF", "Request security availability and quantity.");
            case "COLLATERAL_PLEDGE_REQUESTED" -> new Mapping(2, "Collateral Inquiry", "BB", "Submit collateral details and eligibility requirements.");
            case "COLLATERAL_PLEDGED", "COLLATERAL_RELEASED" -> new Mapping(3, "Collateral Report", "BA", "Report collateral allocation, release, or valuation outcome.");
            case "LOAN_REQUESTED" -> new Mapping(4, "Trade Capture Report", "AE", "Submit agreed loan quantity, lending fee, and settlement details.");
            case "LOAN_SETTLED", "LOAN_REJECTED" -> new Mapping(5, "Trade Confirmation", "AK", "Confirm the lending contract outcome and applied MSLA checks.");
            default -> new Mapping(6, "Security Status", "f", "Report the current securities-lending workflow status.");
        };
    }

    private record FixField(String tag, String name, String value) {}
    private record Mapping(int sequence, String messageName, String businessFixType, String description) {}
}
