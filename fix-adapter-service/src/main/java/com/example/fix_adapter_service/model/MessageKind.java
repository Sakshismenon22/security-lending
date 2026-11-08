package com.example.fix_adapter_service.model;

public enum MessageKind {
    COMMAND("U1"), CONFIRMATION("U2"), RECEIPT("U3");
    private final String fixType;
    MessageKind(String fixType) {this.fixType=fixType;}
    public String fixType() {return fixType;}
    public static MessageKind fromFixType(String type) {
        for(var kind:values())if(kind.fixType.equals(type))return kind;
        throw new IllegalArgumentException("Unsupported FIX message type: "+type);
    }
}
