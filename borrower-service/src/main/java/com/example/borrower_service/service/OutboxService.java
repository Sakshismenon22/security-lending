package com.example.borrower_service.service;

import static com.example.borrower_service.repository.BorrowerRepository.*;

import com.example.borrower_service.model.*;
import com.example.borrower_service.repository.BorrowerRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class OutboxService {
    private final BorrowerRepository db;
    private final KafkaTemplate<String, String> kafka;
    private final SimpMessagingTemplate websocket;
    private final boolean kafkaEnabled;
    private final String topic, commandsTopic;

    public OutboxService(
            BorrowerRepository db,
            KafkaTemplate<String, String> kafka,
            SimpMessagingTemplate websocket,
            @Value("${borrower.kafka-enabled:false}") boolean kafkaEnabled,
            @Value("${borrower.events-topic}") String topic,
            @Value("${borrower.commands-topic}") String commandsTopic) {
        this.db = db;
        this.kafka = kafka;
        this.websocket = websocket;
        this.kafkaEnabled = kafkaEnabled;
        this.topic = topic;
        this.commandsTopic = commandsTopic;
    }

    public List<Map<String, Object>> pending() {
        var pending = eq("wsSent", false);
        if (kafkaEnabled) pending = either(pending, eq("published", false));
        return db.rows(OutboxEvent.class, Sort.by("id"), 100, pending);
    }

    @Transactional
    public void publish(long id) throws Exception {
        var row = db.lockedRow(OutboxEvent.class, id);
        long org = n(row, "organization_id");
        String payload = row.get("payload").toString();
        if (!Boolean.TRUE.equals(row.get("ws_sent"))) {
            websocket.convertAndSend("/topic/organizations/" + org + "/events", payload);
            db.edit(
                    OutboxEvent.class,
                    id,
                    entity -> {
                        entity.setWsSent(true);
                    });
        }
        if (kafkaEnabled && !Boolean.TRUE.equals(row.get("published"))) {
            kafka.send(
                            isCommand(row.get("event_type").toString()) ? commandsTopic : topic,
                            String.valueOf(org),
                            payload)
                    .get(10, TimeUnit.SECONDS);
            db.edit(
                    OutboxEvent.class,
                    id,
                    entity -> {
                        entity.setPublished(true);
                    });
        }
    }

    private boolean isCommand(String type) {
        return type.endsWith("_REQUESTED") || type.equals("AGREEMENT_PROPOSED");
    }
}
