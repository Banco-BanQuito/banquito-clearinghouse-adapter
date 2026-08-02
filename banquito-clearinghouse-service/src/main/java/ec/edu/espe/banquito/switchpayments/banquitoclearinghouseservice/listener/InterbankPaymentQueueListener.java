package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.InboundPaymentMessage;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.service.InboundPaymentService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Parte 2 (interoperabilidad REST real): consume el topico interbank-payments-received,
 * publicado por InterbankPaymentPublisher tras el ack HTTP rapido (RCVD), y ejecuta la
 * orquestacion completa de InboundPaymentService.process (banco valido, asientos Vostro,
 * credito al cliente) de forma desacoplada del hilo de la respuesta HTTP. Mismo patron y
 * ciclo de vida que ClearingQueueListener (Fase 5).
 */
@Component
@ConditionalOnProperty(
        prefix = "clearing.pubsub-listener",
        name = "enabled",
        havingValue = "true")
public class InterbankPaymentQueueListener {

    private static final Logger log = LoggerFactory.getLogger(InterbankPaymentQueueListener.class);

    private final InboundPaymentService inboundPaymentService;
    private final ObjectMapper objectMapper;
    private final String projectId;
    private final String subscription;
    private Subscriber subscriber;

    public InterbankPaymentQueueListener(InboundPaymentService inboundPaymentService,
                                          ObjectMapper objectMapper,
                                          @Value("${pubsub.project-id}") String projectId,
                                          @Value("${pubsub.subscription.interbank-payments-received}") String subscription) {
        this.inboundPaymentService = inboundPaymentService;
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

    void receive(PubsubMessage pubsubMessage, AckReplyConsumer consumer) {
        try {
            InboundPaymentMessage message = objectMapper.readValue(
                    pubsubMessage.getData().toByteArray(), InboundPaymentMessage.class);
            inboundPaymentService.process(message);
            consumer.ack();
        } catch (Exception e) {
            log.error("Error procesando pago interbancario desde Pub/Sub", e);
            consumer.nack();
        }
    }
}
