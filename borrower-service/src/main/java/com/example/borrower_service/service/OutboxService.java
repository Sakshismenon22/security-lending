package com.example.borrower_service.service;

import com.example.borrower_service.repository.BorrowerRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static com.example.borrower_service.repository.BorrowerRepository.*;

@Service
public class OutboxService {
    private final BorrowerRepository db;private final KafkaTemplate<String,String> kafka;private final SimpMessagingTemplate websocket;
    private final boolean kafkaEnabled;private final String topic,commandsTopic;
    public OutboxService(BorrowerRepository db,KafkaTemplate<String,String> kafka,SimpMessagingTemplate websocket,
        @Value("${borrower.kafka-enabled:false}") boolean kafkaEnabled,@Value("${borrower.events-topic}") String topic,
        @Value("${borrower.commands-topic}") String commandsTopic) {
        this.db=db;this.kafka=kafka;this.websocket=websocket;this.kafkaEnabled=kafkaEnabled;this.topic=topic;this.commandsTopic=commandsTopic;
    }
    public List<Map<String,Object>> pending() {
        return db.list("select id from outbox_event where ws_sent=false"+(kafkaEnabled?" or published=false":"")+" order by id limit 100");
    }
    @Transactional
    public void publish(long id) throws Exception {
        var row=db.one("select * from outbox_event where id=? for update",id);long org=n(row,"organization_id");String payload=row.get("payload").toString();
        if(!Boolean.TRUE.equals(row.get("ws_sent"))) {
            websocket.convertAndSend("/topic/organizations/"+org+"/events",payload);db.update("update outbox_event set ws_sent=true where id=?",id);
        }
        if(kafkaEnabled && !Boolean.TRUE.equals(row.get("published"))) {
            kafka.send(isCommand(row.get("event_type").toString())?commandsTopic:topic,String.valueOf(org),payload).get(10,TimeUnit.SECONDS);db.update("update outbox_event set published=true where id=?",id);
        }
    }
    private boolean isCommand(String type) {return type.endsWith("_REQUESTED") || type.equals("AGREEMENT_PROPOSED");}
}
