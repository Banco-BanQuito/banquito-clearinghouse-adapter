package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.BatchLineMessage;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.OffUsPaymentMessage;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.service.OffUsConsumerService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

@Component
@ConditionalOnProperty(
        prefix = "clearing.pubsub-listener",
        name = "enabled",
        havingValue = "true")
public class ClearingQueueListener {

    private static final Logger log = LoggerFactory.getLogger(ClearingQueueListener.class);

    private final OffUsConsumerService offUsConsumerService;
    private final ObjectMapper objectMapper;
    private final String projectId;
    private final String subscription;
    private Subscriber subscriber;

    public ClearingQueueListener(OffUsConsumerService offUsConsumerService,
                                 ObjectMapper objectMapper,
                                 @Value("${pubsub.project-id}") String projectId,
                                 @Value("${pubsub.subscription.off-us}") String subscription) {
        this.offUsConsumerService = offUsConsumerService;
        this.objectMapper = objectMapper;
        this.projectId = projectId;
        this.subscription = subscription;
    }

    @PostConstruct
    public void start() {
        MessageReceiver receiver = this::receive;
        subscriber = Subscriber.newBuilder(ProjectSubscriptionName.of(projectId, subscription), receiver).build();
        subscriber.startAsync().awaitRunning();
        log.info("Pub/Sub subscriber iniciado para {}", subscription);
    }

    @PreDestroy
    public void stop() {
        if (subscriber != null) {
            subscriber.stopAsync();
        }
    }

    private void receive(PubsubMessage pubsubMessage, AckReplyConsumer consumer) {
        try {
            OffUsPaymentMessage message = readMessage(pubsubMessage);
            offUsConsumerService.process(message);
            consumer.ack();
        } catch (Exception e) {
            log.error("Error procesando mensaje de clearing desde Pub/Sub", e);
            consumer.nack();
        }
    }

    private OffUsPaymentMessage readMessage(PubsubMessage pubsubMessage) throws Exception {
        byte[] payload = pubsubMessage.getData().toByteArray();
        if (objectMapper.readTree(payload).has("batch_id")) {
            return adapt(objectMapper.readValue(payload, BatchLineMessage.class));
        }
        return objectMapper.readValue(payload, OffUsPaymentMessage.class);
    }

    private OffUsPaymentMessage adapt(BatchLineMessage line) {
        OffUsPaymentMessage message = new OffUsPaymentMessage();
        message.setBatchId(UUID.fromString(line.batchId()));
        message.setTransactionId(UUID.randomUUID());
        message.setRoutingCode(line.routingCode());
        message.setOriginAccount(line.originatingAccount());
        message.setDestinationAccount(line.accountDestination());
        message.setAmount(line.amount());
        message.setCurrency("USD");
        message.setConcept(line.reference());
        message.setValueDate(LocalDate.now());
        return message;
    }
}
