package com.example.fix_adapter_service.service;

import com.example.fix_adapter_service.config.AdapterProperties;
import com.example.fix_adapter_service.dto.DecodedFixMessage;
import com.example.fix_adapter_service.model.*;
import org.springframework.stereotype.Service;
import quickfix.*;
import quickfix.field.MsgType;
import java.nio.*;
import java.nio.charset.*;
import java.util.Base64;

/** Explicit bilateral FIX 4.4 extension; it is not a standard securities-lending FIX message mapping. */
@Service
public class FixMessageCodec {
    public static final int EVENT_ID=9001;
    public static final int PAYLOAD=9002;
    public static final int PAYLOAD_HASH=9003;
    private final AdapterProperties properties;
    public FixMessageCodec(AdapterProperties properties) {this.properties=properties;}
    public Message business(OutboundMessage outbound) {
        var message=new Message();message.getHeader().setString(MsgType.FIELD,outbound.getMessageKind().fixType());
        message.setString(EVENT_ID,outbound.getEventId());
        message.setString(PAYLOAD,Base64.getEncoder().encodeToString(outbound.getPayload().getBytes(StandardCharsets.UTF_8)));
        message.setString(PAYLOAD_HASH,outbound.getPayloadHash());return message;
    }
    public Message receipt(String eventId,String hash) {
        var message=new Message();message.getHeader().setString(MsgType.FIELD,MessageKind.RECEIPT.fixType());
        message.setString(EVENT_ID,eventId);message.setString(PAYLOAD_HASH,hash);return message;
    }
    public DecodedFixMessage decode(Message message) throws FieldNotFound {
        var kind=MessageKind.fromFixType(message.getHeader().getString(MsgType.FIELD));
        String eventId=message.getString(EVENT_ID),hash=message.getString(PAYLOAD_HASH);
        if(!eventId.matches("[A-Za-z0-9_.:-]{1,120}") || !hash.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid FIX transport identity/hash");
        if(kind==MessageKind.RECEIPT) {
            if(message.isSetField(PAYLOAD))throw new IllegalArgumentException("A receipt must not contain a business payload");
            return new DecodedFixMessage(kind,eventId,hash,null);
        }
        String encoded=message.getString(PAYLOAD);
        if(encoded.length()>((properties.getMaxPayloadBytes()+2)/3)*4)throw new IllegalArgumentException("Encoded FIX payload too large");
        byte[] bytes=Base64.getDecoder().decode(encoded);
        if(bytes.length>properties.getMaxPayloadBytes())throw new IllegalArgumentException("Decoded FIX payload too large");
        try {
            String payload=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            if(!EnvelopeService.hash(payload).equals(hash))throw new IllegalArgumentException("FIX payload checksum mismatch");
            return new DecodedFixMessage(kind,eventId,hash,payload);
        } catch(CharacterCodingException e){throw new IllegalArgumentException("Invalid UTF-8 FIX payload",e);}
    }
}
