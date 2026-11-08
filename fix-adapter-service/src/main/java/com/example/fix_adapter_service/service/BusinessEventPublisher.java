package com.example.fix_adapter_service.service;

public interface BusinessEventPublisher {
    void publish(String topic,String key,String payload) throws Exception;
    default void publish(String topic,String key,String payload,java.util.Map<String,String> headers) throws Exception {publish(topic,key,payload);}
}
