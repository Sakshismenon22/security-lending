package com.example.fix_adapter_service;

import com.example.fix_adapter_service.model.DeliveryState;
import com.example.fix_adapter_service.repository.*;
import com.example.fix_adapter_service.service.FixEngineService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.kafka.core.*;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

/** Actual embedded Kafka plus actual TCP FIX sessions. No transport, broker or persistence mocks. */
class LiveBridgeIntegrationTests {
    @TempDir Path directory;
    @Test void roundTripAcrossRealFixAndKafkaSurvivesBorrowerRestart() throws Exception {
        var broker=new EmbeddedKafkaKraftBroker(1,1,"borrower.commands.v1","lender.commands.v1","lender.confirmations.v1","borrower.confirmations.v1","borrower.commands.v1.DLT","lender.confirmations.v1.DLT");
        broker.brokerProperties(Map.of("auto.create.topics.enable","false","num.network.threads","2","num.io.threads","2"));
        ConfigurableApplicationContext borrower=null,lender=null;Consumer<String,String> lenderObserver=null,borrowerObserver=null,rejectedObserver=null;
        String database=UUID.randomUUID().toString().replace("-","");int port;
        try(var socket=new ServerSocket(0)){port=socket.getLocalPort();}
        broker.afterPropertiesSet();
        try {
            lender=start("LENDER",broker.getBrokersAsString(),port,database);
            borrower=start("BORROWER",broker.getBrokersAsString(),port,database);
            var firstBorrower=borrower;var lenderContext=lender;
            waitFor(()->firstBorrower.getBean(FixEngineService.class).sessions().get(0).loggedOn() && lenderContext.getBean(FixEngineService.class).sessions().get(0).loggedOn(),"Both FIX sessions must log on");
            lenderObserver=observer(broker,"observe-lender-"+database,"lender.commands.v1");borrowerObserver=observer(broker,"observe-borrower-"+database,"borrower.confirmations.v1");
            rejectedObserver=observer(broker,"observe-rejected-"+database,"borrower.commands.v1.DLT");
            String commandId="wire-command-"+database,body=MessageCodecTests.command(commandId);
            template(borrower).send("borrower.commands.v1","1",body).get(10,TimeUnit.SECONDS);
            var command=KafkaTestUtils.getSingleRecord(lenderObserver,"lender.commands.v1",Duration.ofSeconds(30));
            assertEquals(commandId,MessageCodecTests.JSON.readTree(command.value()).get("eventId").asText());assertEquals("1",command.key());
            assertEquals("BORROWER_DEMO",new String(command.headers().lastHeader("x-counterparty-id").value(),java.nio.charset.StandardCharsets.UTF_8));
            waitFor(()->firstBorrower.getBean(OutboundMessageRepository.class).findByEventId(commandId).map(m->m.getState()==DeliveryState.ACKNOWLEDGED).orElse(false),"Borrower must receive the durable transport receipt");
            String confirmationId="wire-confirmation-"+database;
            template(lender).send("lender.confirmations.v1","1",MessageCodecTests.confirmation(confirmationId)).get(10,TimeUnit.SECONDS);
            var confirmation=KafkaTestUtils.getSingleRecord(borrowerObserver,"borrower.confirmations.v1",Duration.ofSeconds(30));
            var received=MessageCodecTests.JSON.readTree(confirmation.value());
            assertEquals(confirmationId,received.get("eventId").asText());assertEquals(1,received.get("version").intValue());assertFalse(received.has("borrowerExternalId"));
            waitFor(()->lenderContext.getBean(OutboundMessageRepository.class).findByEventId(confirmationId).map(m->m.getState()==DeliveryState.ACKNOWLEDGED).orElse(false),"Lender must receive the confirmation transport receipt");
            int sequence=borrower.getBean(FixEngineService.class).sessions().get(0).nextSenderSequence();
            borrower.close();borrower=null;
            borrower=start("BORROWER",broker.getBrokersAsString(),port,database);var restarted=borrower;
            waitFor(()->restarted.getBean(FixEngineService.class).sessions().get(0).loggedOn(),"Borrower FIX session must reconnect after restart");
            assertTrue(borrower.getBean(FixEngineService.class).sessions().get(0).nextSenderSequence()>=sequence,"FIX sequence numbers must survive restart");
            // Replaying the original Kafka event after restart must not recreate a delivery.
            template(borrower).send("borrower.commands.v1","1",body).get(10,TimeUnit.SECONDS);
            String secondId="wire-second-"+database;
            template(borrower).send("borrower.commands.v1","1",MessageCodecTests.command(secondId)).get(10,TimeUnit.SECONDS);
            var second=KafkaTestUtils.getSingleRecord(lenderObserver,"lender.commands.v1",Duration.ofSeconds(30));
            assertEquals(secondId,MessageCodecTests.JSON.readTree(second.value()).get("eventId").asText());
            waitFor(()->restarted.getBean(OutboundMessageRepository.class).findByEventId(secondId).map(m->m.getState()==DeliveryState.ACKNOWLEDGED).orElse(false),"Second command must be acknowledged");
            assertEquals(2,borrower.getBean(OutboundMessageRepository.class).count());assertEquals(2,lender.getBean(InboundMessageRepository.class).count());
            assertEquals(1,borrower.getBean(InboundMessageRepository.class).count());
            template(borrower).send("borrower.commands.v1","1","{}").get(10,TimeUnit.SECONDS);
            assertEquals("{}",KafkaTestUtils.getSingleRecord(rejectedObserver,"borrower.commands.v1.DLT",Duration.ofSeconds(20)).value());
            assertEquals(2,borrower.getBean(OutboundMessageRepository.class).count());
        } finally {
            if(rejectedObserver!=null)rejectedObserver.close();if(lenderObserver!=null)lenderObserver.close();if(borrowerObserver!=null)borrowerObserver.close();
            if(borrower!=null)borrower.close();if(lender!=null)lender.close();broker.destroy();
        }
    }
    private ConfigurableApplicationContext start(String side,String kafka,int port,String database) {
        boolean borrower=side.equals("BORROWER");String local=borrower?"BORROWER_DEMO":"LENDER_DEMO",remote=borrower?"LENDER_DEMO":"BORROWER_DEMO";
        return new SpringApplicationBuilder(FixAdapterServiceApplication.class).web(WebApplicationType.NONE).profiles("test").run(
            "--spring.datasource.url=jdbc:h2:mem:wire_"+side+database+";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "--spring.kafka.bootstrap-servers="+kafka,"--adapter.side="+side,"--adapter.institution-id="+local,"--adapter.sender-comp-id="+local,
            "--adapter.fix-enabled=true","--adapter.kafka-enabled=true","--adapter.workers-enabled=true",
            "--adapter.consumer-group=wire-"+side+database,
            "--adapter.outbound-topic="+(borrower?"borrower.commands.v1":"lender.confirmations.v1"),
            "--adapter.inbound-topic="+(borrower?"borrower.confirmations.v1":"lender.commands.v1"),
            "--adapter.routes[0].counterparty-id="+remote,"--adapter.routes[0].target-comp-id="+remote,
            "--adapter.routes[0].organization-ids=1","--adapter.routes[0].host=127.0.0.1","--adapter.routes[0].port="+port,
            "--adapter.listen-port="+port,"--adapter.store-directory="+directory.resolve(side),
            "--adapter.reconnect-seconds=1","--adapter.retry-seconds=1","--adapter.receipt-timeout-seconds=2",
            "--adapter.delivery-poll-ms=50","--adapter.publication-poll-ms=50",
            "--logging.level.org.apache.kafka=ERROR","--logging.level.kafka=ERROR","--logging.level.org.springframework.kafka=WARN");
    }
    private Consumer<String,String> observer(EmbeddedKafkaKraftBroker broker,String group,String topic) {
        var props=KafkaTestUtils.consumerProps(group,"false",broker);props.put("auto.offset.reset","earliest");
        Consumer<String,String> consumer=new DefaultKafkaConsumerFactory<>(props,new StringDeserializer(),new StringDeserializer()).createConsumer();broker.consumeFromAnEmbeddedTopic(consumer,topic);return consumer;
    }
    @SuppressWarnings("unchecked") private KafkaTemplate<String,String> template(ConfigurableApplicationContext context) {return context.getBean(KafkaTemplate.class);}
    private void waitFor(BooleanSupplier condition,String message) throws InterruptedException {
        long deadline=System.nanoTime()+Duration.ofSeconds(25).toNanos();
        while(System.nanoTime()<deadline) {if(condition.getAsBoolean())return;Thread.sleep(50);}
        fail(message);
    }
}
