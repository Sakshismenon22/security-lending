package com.example.fix_adapter_service.service;

import quickfix.Message;

public interface FixTransport {
    boolean loggedOn(String sessionKey);
    boolean send(String sessionKey,Message message) throws Exception;
}
